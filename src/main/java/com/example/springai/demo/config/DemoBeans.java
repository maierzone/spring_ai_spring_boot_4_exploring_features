package com.example.springai.demo.config;

import java.util.List;

import com.example.springai.demo.embedding.HashingEmbeddingModel;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Zentrale Bean-Definitionen, die von mehreren Feature-Demos gemeinsam genutzt
 * werden. Bewusst an einer Stelle gebündelt, damit die einzelnen Feature-Controller
 * schlank bleiben und sich auf das jeweilige Spring-AI-Feature konzentrieren.
 */
@Configuration
public class DemoBeans {

    /**
     * Eigenes, offline arbeitendes Embedding-Modell (siehe {@link HashingEmbeddingModel}).
     * Wird vom Vektorspeicher (RAG) und vom Embedding-Endpunkt verwendet.
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new HashingEmbeddingModel();
    }

    /**
     * Gesprächsspeicher für die Chat-Memory-Demo.
     *
     * <p>{@link MessageWindowChatMemory} hält pro Konversations-ID die letzten N
     * Nachrichten (gleitendes Fenster) und legt sie hier in einem reinen
     * In-Memory-Repository ab. In Produktion könnte man stattdessen ein
     * JDBC-/Cassandra-Repository hinterlegen, ohne den übrigen Code zu ändern.</p>
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
    }

    /**
     * In-Memory-Vektorspeicher für die RAG-Demo, vorbefüllt mit einigen
     * Beispiel-"Wissens"-Dokumenten über Spring AI.
     *
     * <p>{@link SimpleVectorStore} nutzt das übergebene {@link EmbeddingModel}, um
     * beim Hinzufügen jedes Dokument in einen Vektor zu übersetzen, und führt die
     * Ähnlichkeitssuche per Kosinus-Ähnlichkeit durch.</p>
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        store.add(List.of(
                new Document("Spring AI ist ein Framework, das die Integration von "
                        + "KI-Modellen in Spring-Anwendungen vereinfacht."),
                new Document("Der ChatClient ist die zentrale, fluent API von Spring AI, "
                        + "um Prompts an ein Chat-Modell zu senden und Antworten zu erhalten."),
                new Document("Mit Advisors lassen sich wiederkehrende Aspekte wie Chat-Memory, "
                        + "RAG oder Logging als wiederverwendbare Bausteine in die Anfrage einklinken."),
                new Document("RAG (Retrieval Augmented Generation) reichert den Prompt mit "
                        + "relevanten Dokumenten aus einem Vektorspeicher an, bevor er ans Modell geht."),
                new Document("Tool Calling erlaubt es dem Modell, annotierte Java-Methoden "
                        + "aufzurufen, um an aktuelle oder externe Daten zu gelangen.")
        ));
        return store;
    }
}
