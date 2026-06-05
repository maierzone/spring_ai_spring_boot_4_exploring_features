package com.example.springai.demo.feature07_rag;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

/**
 * Liest eine Wissens-Datei aus dem Classpath und zerlegt sie in einzelne
 * {@link Document}s – jeweils ein Absatz (durch Leerzeile getrennt) wird zu einem
 * Dokument.
 *
 * <p>Das ist eine bewusst einfache Variante des "Document Ingestion"-Schritts, der
 * jedem RAG-System vorausgeht. In echten Projekten übernehmen das spezialisierte
 * Reader (PDF, HTML, ...) und TextSplitter aus Spring AI; das Prinzip – Quelle
 * laden, in sinnvolle Häppchen schneiden, in den Vektorspeicher legen – bleibt
 * identisch.</p>
 */
public final class KnowledgeLoader {

    private KnowledgeLoader() {
    }

    /**
     * Zerlegt den Inhalt einer Ressource in Dokumente (ein Absatz = ein Dokument)
     * und versieht jedes Dokument mit dem Quell-Dateinamen als Metadatum.
     */
    public static List<Document> loadParagraphs(Resource resource) {
        return loadParagraphs(resource, resource.getFilename());
    }

    /**
     * Wie {@link #loadParagraphs(Resource)}, erlaubt aber einen explizit
     * angegebenen Quellnamen. Nuetzlich, wenn die Ressource selbst keinen
     * Dateinamen kennt (z.&nbsp;B. ein Upload, der nur als Byte-Strom vorliegt).
     */
    public static List<Document> loadParagraphs(Resource resource, String source) {
        String content;
        try {
            content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            // Eine fehlende Wissensquelle ist ein Konfigurationsfehler -> hart scheitern.
            throw new UncheckedIOException("Wissensquelle nicht lesbar: " + resource.getDescription(), e);
        }

        // An Leerzeilen splitten, leere Absätze verwerfen, Whitespace trimmen.
        return Arrays.stream(content.split("\\R\\s*\\R"))
                .map(String::strip)
                .filter(paragraph -> !paragraph.isBlank())
                .map(paragraph -> Document.builder()
                        .text(paragraph)
                        .metadata("source", source != null ? source : "unknown")
                        .build())
                .toList();
    }
}
