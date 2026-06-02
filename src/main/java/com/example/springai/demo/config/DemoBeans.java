package com.example.springai.demo.config;

import com.example.springai.demo.embedding.HashingEmbeddingModel;
import com.example.springai.demo.feature07_rag.KnowledgeLoader;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

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
        // Wissen wird aus einer Ressourcen-Datei geladen und in Absätze zerlegt
        // (siehe KnowledgeLoader). So liegt der "Content" außerhalb des Codes und
        // kann ohne Neukompilierung gepflegt werden.
        store.add(KnowledgeLoader.loadParagraphs(
                new ClassPathResource("knowledge/spring-ai-faq.md")));
        return store;
    }
}
