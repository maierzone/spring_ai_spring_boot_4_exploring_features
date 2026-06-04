package com.example.springai.demo.feature07_rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.example.springai.demo.embedding.HashingEmbeddingModel;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

/**
 * Zeigt den Kern des RAG-Retrievals offline: Dokumente werden über das eigene
 * Embedding-Modell in den {@link SimpleVectorStore} gelegt, und die
 * Ähnlichkeitssuche liefert für eine Frage das thematisch passendste Dokument.
 */
class VectorStoreRetrievalTest {

    @Test
    void aehnlichkeitssucheFindetPassendesDokument() {
        SimpleVectorStore store = SimpleVectorStore.builder(new HashingEmbeddingModel()).build();
        store.add(List.of(
                new Document("Die Krankenversichertennummer identifiziert einen Versicherten eindeutig."),
                new Document("Der Heilberufsausweis weist einen Arzt persoenlich aus."),
                new Document("Ein ICD-10-Code klassifiziert eine Diagnose maschinell auswertbar.")));

        List<Document> results = store.similaritySearch(
                SearchRequest.builder().query("Wofuer steht die Krankenversichertennummer?").topK(1).build());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getText()).contains("Krankenversichertennummer");
    }
}
