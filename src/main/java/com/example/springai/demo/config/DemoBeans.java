package com.example.springai.demo.config;

import com.example.springai.demo.embedding.HashingEmbeddingModel;
import com.example.springai.demo.feature07_rag.KnowledgeLoader;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
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
     * Gesprächsspeicher für die Chat-Memory-Demo (Feature 5 / Feature B).
     *
     * <p>{@link MessageWindowChatMemory} hält pro Konversations-ID die letzten N
     * Nachrichten (gleitendes Fenster). Das eigentliche Speichern übernimmt das
     * injizierte {@link ChatMemoryRepository}: In der App ist das ein vom
     * {@code spring-ai-starter-model-chat-memory-repository-jdbc} autokonfiguriertes
     * {@code JdbcChatMemoryRepository} (PostgreSQL) – der Gesprächsverlauf
     * <em>überlebt damit einen Neustart</em>. In den Tests greift dasselbe
     * Repository auf eine eingebettete H2-DB zu.</p>
     *
     * <p>Genau das ist der Clou des austauschbaren {@code ChatMemoryRepository}:
     * vom flüchtigen In-Memory- auf einen persistenten JDBC-Speicher umzustellen,
     * ohne den übrigen Code anzufassen.</p>
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    /**
     * In-Memory-Vektorspeicher für die RAG-Demo, vorbefüllt mit einigen
     * Telematik-/eGK-Fachbegriffen als "Wissens"-Dokumenten.
     *
     * <p>{@link SimpleVectorStore} nutzt das übergebene {@link EmbeddingModel}, um
     * beim Hinzufügen jedes Dokument in einen Vektor zu übersetzen, und führt die
     * Ähnlichkeitssuche per Kosinus-Ähnlichkeit durch.</p>
     *
     * <p><b>Profil-Switch (Feature 17):</b> Diese Bean ist mit {@code @Profile("!postgres")}
     * versehen und entfällt, wenn das Profil {@code postgres} aktiv ist.
     * Dann übernimmt der autokonfigurierte {@code PgVectorStore} – die Spring-AI-
     * Vektorspeicher-Autokonfiguration ist {@code @ConditionalOnMissingBean(VectorStore.class)}
     * und greift nur, weil hier keine konkurrierende Bean mehr existiert. Ohne
     * explizites Profil (H2, Default) und in den Tests ({@code @ActiveProfiles("test")})
     * bleibt der offline arbeitende {@code SimpleVectorStore} aktiv.</p>
     */
    @Bean
    @Profile("!postgres")
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        // Wissen wird aus einer Ressourcen-Datei geladen und in Absätze zerlegt
        // (siehe KnowledgeLoader). So liegt der "Content" außerhalb des Codes und
        // kann ohne Neukompilierung gepflegt werden.
        store.add(KnowledgeLoader.loadParagraphs(
                new ClassPathResource("knowledge/telematik-wissen.md")));
        return store;
    }
}
