package com.example.springai.demo.feature20_modularrag;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ModularRagController {

    private final ChatClient chatClient;
    private final VectorStoreDocumentRetriever documentRetriever;

    /**
     * <p>Der {@link ChatClient.Builder} ist prototype-scoped: Über den
     * {@link ObjectProvider} holen wir uns <b>zwei unabhängige</b> Builder-Instanzen.
     * Das ist hier wichtig, weil der äußere Builder den RAG-Advisor als
     * Default-Advisor erhält – würden die inneren Komponenten (Rewrite/Expand)
     * denselben Builder nutzen, liefen ihre LLM-Aufrufe erneut durch die
     * RAG-Pipeline (Rekursion).</p>
     */
    public ModularRagController(ObjectProvider<ChatClient.Builder> builders, VectorStore vectorStore) {
        // Eigener, "sauberer" Builder für die internen Pipeline-Schritte.
        ChatClient.Builder innerBuilder = builders.getObject();

        this.documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(3)
                .build();

        RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(RewriteQueryTransformer.builder()
                        .chatClientBuilder(innerBuilder)
                        .build())
                .queryExpander(MultiQueryExpander.builder()
                        .chatClientBuilder(innerBuilder)
                        .includeOriginal(true)
                        .numberOfQueries(3)
                        .build())
                .documentRetriever(documentRetriever)
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build())
                .build();

        this.chatClient = builders.getObject()
                .defaultAdvisors(ragAdvisor)
                .build();
    }

    @GetMapping("/api/modular-rag")
    public String ask(@RequestParam(defaultValue = "Was bedeutet der eGK-Status GESPERRT?") String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }

    /**
     * Transparenz-Endpunkt analog zu {@code /api/rag/sources} (Feature 7): zeigt,
     * welche Dokumente der {@link VectorStoreDocumentRetriever} – die
     * deterministische Mitte der Pipeline – für eine Frage liefert.
     *
     * <p>Bewusst <b>ohne</b> die LLM-Schritte (Rewrite/Expand): so funktioniert der
     * Endpunkt ohne API-Key und macht den reinen Retrieval-Schritt sichtbar.</p>
     */
    @GetMapping("/api/modular-rag/retrieve")
    public List<RetrievedSource> retrieve(
            @RequestParam(defaultValue = "Wozu dient der Heilberufsausweis?") String question) {

        List<Document> hits = documentRetriever.retrieve(new Query(question));

        return hits.stream()
                .map(doc -> new RetrievedSource(
                        doc.getMetadata().getOrDefault("source", "unknown").toString(),
                        doc.getScore(),
                        doc.getText()))
                .toList();
    }

    /** Ein abgerufenes Dokument inkl. Ähnlichkeits-Score und Quelle. */
    public record RetrievedSource(String source, Double score, String text) {
    }
}
