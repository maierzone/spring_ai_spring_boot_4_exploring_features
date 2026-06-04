package com.example.springai.demo.feature18_docsrag;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * FEATURE 18 – seedet die Tabelle {@code docs_rag} aus dem gemspec-Katalog.
 *
 * <p>Lädt die {@code sitemap.xml}, leitet via {@link SpecCatalog} die Spec-PDFs ab
 * und legt jede noch unbekannte (spec, version) als {@code status='incoming'} an.
 * Bestehende Zeilen bleiben unangetastet – das Seeden ist damit idempotent und
 * kann jederzeit erneut laufen, ohne {@code processed}/{@code error} zu verlieren.</p>
 *
 * <p>Nur unter dem Profil {@code specs} aktiv (braucht die pgvector-/Spec-Umgebung).</p>
 */
@Service
@Profile("specs")
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final JdbcTemplate jdbcTemplate;
    private final RestClient restClient;
    private final String sitemapUrl;

    public CatalogService(JdbcTemplate jdbcTemplate,
                          @Value("${docsrag.sitemap-url:https://gemspec.gematik.de/sitemap.xml}") String sitemapUrl) {
        this.jdbcTemplate = jdbcTemplate;
        // Eigenständiger RestClient – unabhängig von einer (hier nicht
        // autokonfigurierten) RestClient.Builder-Bean.
        this.restClient = RestClient.create();
        this.sitemapUrl = sitemapUrl;
    }

    /**
     * Lädt die Sitemap und fügt neue Katalog-Einträge als {@code incoming} ein.
     *
     * @return Anzahl neu angelegter Zeilen
     */
    public int seed() {
        String sitemap = restClient.get().uri(sitemapUrl).retrieve().body(String.class);
        List<SpecCatalog.SpecDocument> docs = SpecCatalog.parse(sitemap != null ? sitemap : "");

        Set<String> existing = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT spec || '@' || version FROM docs_rag", String.class));

        int inserted = 0;
        for (SpecCatalog.SpecDocument doc : docs) {
            if (existing.contains(doc.spec() + "@" + doc.version())) {
                continue;
            }
            jdbcTemplate.update(
                    "INSERT INTO docs_rag (spec, version, category, pdf_url, status) VALUES (?, ?, ?, ?, 'incoming')",
                    doc.spec(), doc.version(), doc.category(), doc.pdfUrl());
            inserted++;
        }
        log.info("Katalog geseedet: {} Kandidaten in Sitemap, {} neu als 'incoming' angelegt.",
                docs.size(), inserted);
        return inserted;
    }
}
