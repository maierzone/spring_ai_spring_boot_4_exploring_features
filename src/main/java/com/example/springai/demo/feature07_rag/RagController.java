package com.example.springai.demo.feature07_rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
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
    public RagController(ChatClient.Builder builder, VectorStore vectorStore) {
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
}
