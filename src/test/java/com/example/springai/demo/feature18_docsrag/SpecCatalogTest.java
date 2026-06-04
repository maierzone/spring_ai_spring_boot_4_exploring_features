package com.example.springai.demo.feature18_docsrag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Offline-Test der reinen Katalog-Ableitung (kein Netz, keine DB).
 */
class SpecCatalogTest {

    private static final String SITEMAP = """
            <?xml version="1.0" encoding="UTF-8"?>
            <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
              <url><loc>https://gemspec.gematik.de/docs/gemSpec/</loc></url>
              <url><loc>https://gemspec.gematik.de/docs/gemSpec/gemSpec_PKI/gemSpec_PKI_V2.27.0/</loc></url>
              <url><loc>https://gemspec.gematik.de/docs/gemSpec/gemSpec_PKI/latest/</loc></url>
              <url><loc>https://gemspec.gematik.de/docs/gemSpec/gemSpec_Authentisierung_Vers/gemSpec_Authentisierung_Vers_V1.4.1_Aend/</loc></url>
              <url><loc>https://gemspec.gematik.de/docs/gemSpec/gemSpec_Aktensystem_ePAfueralle/gemSpec_Aktensystem_ePAfueralle_V1.2.1_Draft_20240531/</loc></url>
              <url><loc>https://gemspec.gematik.de/docs/gemProdT/gemProdT_Kon/gemProdT_Kon_PTV4_4.8.1-0_V1.0.2/</loc></url>
              <url><loc>https://gemspec.gematik.de/docs/separator_0/</loc></url>
              <url><loc>https://gemspec.gematik.de/releases/Betr_24_1/</loc></url>
            </urlset>
            """;

    @Test
    void leitetNurVersionierteVollDokumenteAbUndBautPdfUrls() {
        List<SpecCatalog.SpecDocument> docs = SpecCatalog.parse(SITEMAP);

        // Erwartet: nur die zwei echten versionierten Voll-Dokumente.
        // Ausgeschlossen: Kategorie-Root, latest, _Aend, _Draft, separator, releases.
        assertThat(docs)
                .extracting(SpecCatalog.SpecDocument::category,
                        SpecCatalog.SpecDocument::spec,
                        SpecCatalog.SpecDocument::version,
                        SpecCatalog.SpecDocument::pdfUrl)
                .containsExactly(
                        tuple("gemSpec", "gemSpec_PKI", "2.27.0",
                                "https://gemspec.gematik.de/downloads/gemSpec/gemSpec_PKI/gemSpec_PKI_V2.27.0.pdf"),
                        tuple("gemProdT", "gemProdT_Kon_PTV4_4.8.1-0", "1.0.2",
                                "https://gemspec.gematik.de/downloads/gemProdT/gemProdT_Kon/gemProdT_Kon_PTV4_4.8.1-0_V1.0.2.pdf"));
    }
}
