package com.example.springai.demo.feature07_rag;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("specs | postgres")
public class RagImportStore {

    private static final Logger log = LoggerFactory.getLogger(RagImportStore.class);

    /** PostgreSQL-Text-Search-Konfiguration. Deutsch = Stemming + Stoppwörter für Fachtext. */
    private static final String TS_CONFIG = "german";

    private final JdbcTemplate jdbcTemplate;

    /** Server-Ordner, aus dem {@code /folder} Dateien einliest. */
    private final Path importDir;

    public RagImportStore(JdbcTemplate jdbcTemplate,
                          @Value("${demo.rag.import-dir:import-docs}") String importDir) {
        this.jdbcTemplate = jdbcTemplate;
        this.importDir = Path.of(importDir).toAbsolutePath();
    }


    @PostConstruct
    void init() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS rag_import (
                    id BIGSERIAL PRIMARY KEY,
                    source TEXT NOT NULL,
                    content TEXT NOT NULL,
                    tsv tsvector GENERATED ALWAYS AS (to_tsvector('%s', content)) STORED
                )""".formatted(TS_CONFIG));
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS rag_import_tsv_idx ON rag_import USING GIN (tsv)");
    }

    // ------------------------------------------------------------------ Import

    /**
     * Liest alle unterstützten Dateien aus dem konfigurierten Server-Ordner ein.
     * Bestehende Importe bleiben erhalten (additiv).
     */
    public Map<String, Object> ingestFolder() {
        try {
            Files.createDirectories(importDir);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Import-Ordner nicht anlegbar: " + importDir, e);
        }
        int added = 0;
        try (var stream = Files.list(importDir)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                if (!isSupported(name)) {
                    continue;
                }
                added += insertChunks(name, readChunks(name, new FileSystemResource(file)));
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException("Import-Ordner nicht lesbar: " + importDir, e);
        }
        log.info("rag-import/folder: {} neue Chunks aus {}", added, importDir);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dir", importDir.toString());
        result.putAll(stats());
        return result;
    }

    /** Verarbeitet direkt hochgeladene Dateien (multipart) in den Import-Speicher. */
    public Map<String, Object> ingestUploads(MultipartFile[] files) {
        int added = 0;
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename();
            if (name == null || name.isBlank() || !isSupported(name)) {
                continue;
            }
            added += insertChunks(name, readChunks(name, toResource(file, name)));
        }
        log.info("rag-import/upload: {} neue Chunks aus {} Datei(en)", added, files.length);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("added", added);
        result.putAll(stats());
        return result;
    }

    /** Leert den Import-Speicher vollständig. */
    public Map<String, Object> clear() {
        jdbcTemplate.execute("TRUNCATE TABLE rag_import RESTART IDENTITY");
        return stats();
    }

    // ------------------------------------------------------------------ Suche

    /**
     * Lexikalische BM25-artige Suche. Reihenfolge der Treffer (das eigentliche
     * Ziel): exakte Teilstring-Treffer zuerst, dann Phrasen-Treffer, dann nach
     * {@code ts_rank_cd}. Der zurückgegebene {@code score} ist auf 0..1 normiert,
     * sodass ein Exaktmatch ~1.0 statt ~0.80 zeigt.
     */
    public List<Hit> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String like = "%" + query.strip() + "%";
        // Die Text-Search-Konfiguration ('german') wird als Literal eingesetzt, nicht
        // als Bind-Parameter: ein getypter JDBC-varchar-Parameter wird von Postgres
        // nicht zuverlassig zur regconfig-Ueberladung von websearch_to_tsquery/
        // phraseto_tsquery aufgeloest. TS_CONFIG ist eine Konstante (kein User-Input),
        // daher kein Injection-Risiko.
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT source, content,
                       ts_rank_cd(tsv, websearch_to_tsquery('%1$s', ?)) AS rank,
                       (tsv @@ phraseto_tsquery('%1$s', ?)) AS phrase_hit,
                       (lower(content) LIKE lower(?)) AS exact_hit
                FROM rag_import
                WHERE tsv @@ websearch_to_tsquery('%1$s', ?)
                   OR lower(content) LIKE lower(?)
                ORDER BY exact_hit DESC, phrase_hit DESC, rank DESC
                LIMIT ?
                """.formatted(TS_CONFIG),
                query, query, like, query, like, topK);

        double maxRank = rows.stream()
                .mapToDouble(r -> ((Number) r.get("rank")).doubleValue())
                .max().orElse(0.0);

        List<Hit> hits = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            boolean exact = Boolean.TRUE.equals(r.get("exact_hit"));
            boolean phrase = Boolean.TRUE.equals(r.get("phrase_hit"));
            double rank = ((Number) r.get("rank")).doubleValue();
            hits.add(new Hit((String) r.get("source"),
                    score(exact, phrase, rank, maxRank),
                    (String) r.get("content")));
        }
        return hits;
    }

    /**
     * Bildet die intuitive Score-Skala: Exaktmatch ~1.0, Phrasentreffer ~0.9,
     * sonst der relative {@code ts_rank} im Bereich 0.5..0.85.
     */
    private double score(boolean exact, boolean phrase, double rank, double maxRank) {
        if (exact) {
            return 1.0;
        }
        if (phrase) {
            return 0.9;
        }
        double relative = maxRank > 0 ? rank / maxRank : 0.0;
        return 0.5 + 0.35 * relative;
    }

    // ------------------------------------------------------------- Hilfsmittel

    /** Aktueller Bestand: Anzahl Chunks und beteiligte Quelldateien (für die UI). */
    public Map<String, Object> stats() {
        Long documents = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rag_import", Long.class);
        List<String> files = jdbcTemplate.queryForList(
                "SELECT DISTINCT source FROM rag_import ORDER BY source", String.class);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("documents", documents != null ? documents : 0L);
        stats.put("files", files);
        return stats;
    }

    private int insertChunks(String source, List<String> chunks) {
        if (chunks.isEmpty()) {
            return 0;
        }
        jdbcTemplate.batchUpdate("INSERT INTO rag_import (source, content) VALUES (?, ?)",
                chunks, chunks.size(),
                (ps, chunk) -> {
                    ps.setString(1, source);
                    ps.setString(2, chunk);
                });
        return chunks.size();
    }

    /** PDF → Seiten; Text (md/txt) → Absätze. Jeweils ein Chunk pro Eintrag. */
    private List<String> readChunks(String name, org.springframework.core.io.Resource resource) {
        if (name.toLowerCase().endsWith(".pdf")) {
            return new PagePdfDocumentReader(resource).read().stream()
                    .map(Document::getText)
                    .filter(t -> t != null && !t.isBlank())
                    .map(String::strip)
                    .toList();
        }
        String text;
        try {
            text = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Datei nicht lesbar: " + name, e);
        }
        return Arrays.stream(text.split("\\R\\s*\\R"))
                .map(String::strip)
                .filter(p -> !p.isBlank())
                .toList();
    }

    private org.springframework.core.io.Resource toResource(MultipartFile file, String name) {
        try {
            if (name.toLowerCase().endsWith(".pdf")) {
                Path tmp = Files.createTempFile("rag-import-", ".pdf");
                tmp.toFile().deleteOnExit();
                file.transferTo(tmp);
                return new FileSystemResource(tmp);
            }
            return new org.springframework.core.io.ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return name;
                }
            };
        }
        catch (IOException e) {
            throw new UncheckedIOException("Upload nicht verarbeitbar: " + name, e);
        }
    }

    private boolean isSupported(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".md") || n.endsWith(".markdown") || n.endsWith(".txt") || n.endsWith(".pdf");
    }

    /** Ein Treffer der lexikalischen Suche – gleiche Form wie {@code RetrievedSource}. */
    public record Hit(String source, Double score, String text) {
    }
}
