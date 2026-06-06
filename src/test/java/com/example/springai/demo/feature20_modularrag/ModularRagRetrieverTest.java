package com.example.springai.demo.feature20_modularrag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.example.springai.demo.embedding.HashingEmbeddingModel;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SimpleVectorStore;

/**
 * Prüft den deterministischen Kern der modularen RAG-Pipeline (Feature 20) offline:
 * den {@link VectorStoreDocumentRetriever}. Die LLM-Schritte (Rewrite/Expansion)
 * bleiben bewusst außen vor – getestet wird, dass der Retriever für eine
 * {@link Query} das thematisch passende Dokument aus dem {@link SimpleVectorStore}
 * liefert (Embedding via {@link HashingEmbeddingModel}, kein API-Key nötig).
 */
class ModularRagRetrieverTest {

    @Test
    void retrieverFindetPassendesDokument() {
        SimpleVectorStore store = SimpleVectorStore.builder(new HashingEmbeddingModel()).build();
        store.add(List.of(
                new Document("Die Krankenversichertennummer identifiziert einen Versicherten eindeutig."),
                new Document("Der Heilberufsausweis weist einen Arzt persoenlich aus."),
                new Document("Ein ICD-10-Code klassifiziert eine Diagnose maschinell auswertbar.")));

        VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(store)
                .topK(1)
                .build();

        List<Document> results = retriever.retrieve(
                new Query("Wofuer steht die Krankenversichertennummer?"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getText()).contains("Krankenversichertennummer");
    }
}
