package com.example.springai.demo.feature18_docsrag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * FEATURE 18 – Hybrid-Suche ueber den Spec-Vektorspeicher.
 *
 * <p>Die reine Vektorsuche trifft bei Fachtext mit vielen Bezeichnern (Feldnamen,
 * OIDs, Fehlercodes, {@code gemSpec_*}, Kapitelnummern) schlecht: Embeddings sind
 * gut in <em>Bedeutung</em>, schwach bei <em>exakten Tokens</em>. Diese Suche
 * kombiniert deshalb zwei Signale und fusioniert sie (Reciprocal Rank Fusion):</p>
 * <ul>
 *   <li><b>lexikalisch</b> – PostgreSQL-Volltext ({@code tsvector}, Konfiguration
 *       {@code german}) plus exakter Teilstring-Treffer ({@code pg_trgm}-beschleunigt);</li>
 *   <li><b>semantisch</b> – Cosine-Naehe im pgvector-Index (gleiches Embedding-Modell
 *       wie bei der Ingestion).</li>
 * </ul>
 *
 * <p>Score-Skala bewusst intuitiv (wie im {@code RagImportStore}): exakter
 * Teilstring ~1.0, Phrasentreffer ~0.9, sonst der fusionierte Rang in 0.5..0.85 –
 * „exakt, sonst am aehnlichsten".</p>
 *
 * <p>Der Volltext-Index wird als <b>zusaetzliche</b> Spalte auf die von Spring AI
 * verwaltete Tabelle {@code spec_vector_store} gelegt (Spring AI ignoriert eigene
 * Zusatzspalten) – ohne Re-Embedding, da {@code tsv} aus {@code content} generiert
 * wird. Nur unter Profil {@code specs} aktiv.</p>
 */
@Service
@Profile("specs")
public class SpecSearchService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SpecSearchService.class);

    /** Text-Search-Konfiguration (Konstante, kein User-Input -> als SQL-Literal einsetzbar). */
    private static final String TS_CONFIG = "german";
    /** Ueberholen: aus beiden Kanaelen je so viele Kandidaten ziehen, vor der Fusion. */
    private static final int CANDIDATES = 40;
    /** RRF-Daempfung; klassischer Wert, macht obere Raenge weniger dominant. */
    private static final int RRF_K = 60;

    private final JdbcTemplate jdbc;
    private final EmbeddingModel embeddingModel;

    public SpecSearchService(JdbcTemplate jdbc, EmbeddingModel embeddingModel) {
        this.jdbc = jdbc;
        this.embeddingModel = embeddingModel;
    }

    /** Ein Suchtreffer fuer die UI. */
    public record Hit(String source, double score, String text) {
    }

    /**
     * Legt – nach dem Start (also nachdem Spring AIs PgVectorStore die Tabelle
     * angelegt hat) – die lexikalischen Hilfsstrukturen idempotent an: eine
     * generierte {@code tsvector}-Spalte mit GIN-Index sowie einen Trigram-Index
     * fuer schnelle Teilstring-Treffer.
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbc.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            jdbc.execute("""
                    ALTER TABLE spec_vector_store
                    ADD COLUMN IF NOT EXISTS tsv tsvector
                    GENERATED ALWAYS AS (to_tsvector('%s', content)) STORED
                    """.formatted(TS_CONFIG));
            jdbc.execute("CREATE INDEX IF NOT EXISTS spec_vector_store_tsv_idx "
                    + "ON spec_vector_store USING GIN (tsv)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS spec_vector_store_trgm_idx "
                    + "ON spec_vector_store USING GIN (content gin_trgm_ops)");
            log.info("Hybrid-Suche: Volltext-/Trigram-Index auf spec_vector_store bereit.");
        } catch (Exception e) {
            // Nicht fatal: ohne die Indizes funktioniert die Vektorsuche weiter.
            log.warn("Konnte lexikalische Indizes nicht anlegen ({}). Hybrid-Suche faellt "
                    + "auf reine Vektorsuche zurueck.", e.getMessage());
        }
    }

    /**
     * Hybrid-Suche: lexikalische und semantische Kandidaten getrennt holen, per RRF
     * fusionieren, exakte/Phrasen-Treffer nach oben heben, Top-K zurueckgeben.
     */
    public List<Hit> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Map<String, Cand> byId = new LinkedHashMap<>();

        // --- Kanal 1: semantisch (pgvector) ------------------------------------
        String qvec = toVectorLiteral(embeddingModel.embed(query));
        List<Map<String, Object>> vecRows = jdbc.queryForList("""
                SELECT id::text AS id, content, metadata->>'source' AS source
                FROM spec_vector_store
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT ?
                """, qvec, CANDIDATES);
        for (int rank = 0; rank < vecRows.size(); rank++) {
            Map<String, Object> r = vecRows.get(rank);
            cand(byId, r).vecRank = rank;
        }

        // --- Kanal 2: lexikalisch (Volltext + exakter Teilstring) --------------
        String like = "%" + query.strip() + "%";
        List<Map<String, Object>> lexRows = jdbc.queryForList("""
                SELECT id::text AS id, content, metadata->>'source' AS source,
                       (lower(content) LIKE lower(?)) AS exact_hit,
                       (tsv @@ phraseto_tsquery('%1$s', ?)) AS phrase_hit
                FROM spec_vector_store
                WHERE tsv @@ websearch_to_tsquery('%1$s', ?)
                   OR lower(content) LIKE lower(?)
                ORDER BY exact_hit DESC, phrase_hit DESC,
                         ts_rank_cd(tsv, websearch_to_tsquery('%1$s', ?)) DESC
                LIMIT ?
                """.formatted(TS_CONFIG),
                like, query, query, like, query, CANDIDATES);
        for (int rank = 0; rank < lexRows.size(); rank++) {
            Map<String, Object> r = lexRows.get(rank);
            Cand c = cand(byId, r);
            c.lexRank = rank;
            c.exact = Boolean.TRUE.equals(r.get("exact_hit"));
            c.phrase = Boolean.TRUE.equals(r.get("phrase_hit"));
        }

        // --- Fusion: RRF als Basis, exakte/Phrasentreffer auf die intuitive Skala
        double maxRrf = byId.values().stream().mapToDouble(Cand::rrf).max().orElse(0.0);
        List<Hit> hits = new ArrayList<>(byId.size());
        for (Cand c : byId.values()) {
            hits.add(new Hit(c.source, score(c, maxRrf), c.content));
        }
        hits.sort((a, b) -> Double.compare(b.score(), a.score()));
        return hits.size() > topK ? hits.subList(0, topK) : hits;
    }

    /** Exakt ~1.0, Phrase ~0.9, sonst fusionierter Rang relativ in 0.5..0.85. */
    private double score(Cand c, double maxRrf) {
        if (c.exact) {
            return 1.0;
        }
        if (c.phrase) {
            return 0.9;
        }
        double relative = maxRrf > 0 ? c.rrf() / maxRrf : 0.0;
        return 0.5 + 0.35 * relative;
    }

    private Cand cand(Map<String, Cand> byId, Map<String, Object> row) {
        return byId.computeIfAbsent((String) row.get("id"), id -> {
            Cand c = new Cand();
            c.content = (String) row.get("content");
            c.source = (String) row.get("source");
            return c;
        });
    }

    /** float[] -> pgvector-Literal "[0.1,0.2,...]" (Punkt als Dezimaltrenner). */
    private static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.US, "%.6f", v[i]));
        }
        return sb.append(']').toString();
    }

    /** Zwischenstand eines Kandidaten waehrend der Fusion. */
    private static final class Cand {
        String content;
        String source;
        int vecRank = -1;
        int lexRank = -1;
        boolean exact;
        boolean phrase;

        /** Reciprocal Rank Fusion ueber beide Kanaele. */
        double rrf() {
            double s = 0.0;
            if (vecRank >= 0) {
                s += 1.0 / (RRF_K + vecRank);
            }
            if (lexRank >= 0) {
                s += 1.0 / (RRF_K + lexRank);
            }
            return s;
        }
    }
}
