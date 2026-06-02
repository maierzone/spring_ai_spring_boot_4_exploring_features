package com.example.springai.demo.feature07_rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;

/**
 * Stellt sicher, dass die Wissensdatei korrekt in einzelne Dokumente (ein Absatz =
 * ein Dokument) zerlegt und mit Quell-Metadaten versehen wird.
 */
class KnowledgeLoaderTest {

    @Test
    void zerlegtWissensdateiInAbsaetzeMitQuelle() {
        List<Document> documents = KnowledgeLoader.loadParagraphs(
                new ClassPathResource("knowledge/spring-ai-faq.md"));

        // Mehrere Absätze, keine leeren.
        assertThat(documents).hasSizeGreaterThanOrEqualTo(5);
        assertThat(documents).allSatisfy(doc -> {
            assertThat(doc.getText()).isNotBlank();
            assertThat(doc.getMetadata()).containsEntry("source", "spring-ai-faq.md");
        });

        // Inhaltliche Stichprobe.
        assertThat(documents).anySatisfy(doc ->
                assertThat(doc.getText()).contains("Retrieval Augmented Generation"));
    }
}
