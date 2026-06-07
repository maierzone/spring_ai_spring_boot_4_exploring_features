package com.example.springai.demo.feature07_rag;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

public final class KnowledgeLoader {

    private KnowledgeLoader() {
    }

    /**
     * Zerlegt den Inhalt einer Ressource in Dokumente (ein Absatz = ein Dokument)
     * und versieht jedes Dokument mit dem Quell-Dateinamen als Metadatum.
     */
    public static List<Document> loadParagraphs(Resource resource) {
        String content;
        try {
            content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            // Eine fehlende Wissensquelle ist ein Konfigurationsfehler -> hart scheitern.
            throw new UncheckedIOException("Wissensquelle nicht lesbar: " + resource.getDescription(), e);
        }

        String source = resource.getFilename();
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
