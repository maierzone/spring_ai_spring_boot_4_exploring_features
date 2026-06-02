package com.example.springai.demo.feature07_rag;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FEATURE 7 – RAG (Retrieval Augmented Generation).
 *
 * <p>Damit das Modell über eigenes, nicht antrainiertes Wissen antworten kann,
 * sucht der {@link QuestionAnswerAdvisor} zunächst die zur Frage passendsten
 * Dokumente im {@link VectorStore} (Ähnlichkeitssuche über Embeddings) und hängt
 * sie als Kontext an den Prompt. Das Modell antwortet dann auf Basis dieses
 * abgerufenen Wissens – das reduziert Halluzinationen und hält Antworten aktuell.</p>
 *
 * <p>Der Vektorspeicher wird in {@code DemoBeans} mit einigen Spring-AI-Fakten
 * vorbefüllt. Beispiel: {@code GET /api/rag?question=Was ist der ChatClient?}</p>
 */
@RestController
public class RagController {

    private final ChatClient chatClient;

    /**
     * Der RAG-Advisor erhält den Vektorspeicher und wird als Default-Advisor
     * registriert, sodass jede Anfrage automatisch mit Kontext angereichert wird.
     */
    private final VectorStore vectorStore;

    public RagController(ChatClient.Builder builder, VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.chatClient = builder
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .build();
    }

    @GetMapping("/api/rag")
    public String ask(@RequestParam(defaultValue = "Was ist Spring AI?") String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }

    /**
     * Transparenz-Endpunkt: zeigt, WELCHE Dokumente die Ähnlichkeitssuche zur
     * Frage liefert – also genau den Kontext, den der QuestionAnswerAdvisor an den
     * Prompt hängen würde. Für Entwickler beim Debuggen von RAG ("warum antwortet
     * das Modell so?") meist wichtiger als die Antwort selbst.
     *
     * <p>Funktioniert ohne LLM/API-Key, da nur der Vektorspeicher befragt wird.</p>
     */
    @GetMapping("/api/rag/sources")
    public List<RetrievedSource> sources(
            @RequestParam(defaultValue = "Was ist RAG?") String question,
            @RequestParam(defaultValue = "2") int topK) {

        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(topK).build());

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
