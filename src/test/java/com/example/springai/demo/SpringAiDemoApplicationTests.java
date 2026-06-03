package com.example.springai.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integrations-Smoke-Test: Startet den kompletten Spring-Kontext und stellt sicher,
 * dass alle zehn Feature-Controller sowie die Spring-AI-Autokonfiguration (Anthropic)
 * sauber verdrahtet werden.
 *
 * <p>Es wird kein echter Modellaufruf ausgeführt – das Hochfahren des Kontexts geht
 * nicht ins Netz und benötigt daher keinen gültigen API-Key (der Platzhalter aus
 * {@code application.properties} genügt).</p>
 *
 * <p>{@code @ActiveProfiles("test")} überschreibt das Laufzeit-Profil {@code pgvector}:
 * statt des PgVectorStore (Postgres) bleibt der offline {@code SimpleVectorStore}
 * auf H2 aktiv, sodass dieser Test ohne Docker läuft (Feature 17).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SpringAiDemoApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        // Kern-Beans von Spring AI müssen vorhanden sein.
        assertThat(context.getBean(ChatClient.Builder.class)).isNotNull();
        assertThat(context.getBean(EmbeddingModel.class)).isNotNull();
        assertThat(context.getBean(VectorStore.class)).isNotNull();
    }

    @Test
    void alleFeatureControllerSindRegistriert() {
        // Pro Feature ein Controller -> Stichprobe über alle Pakete.
        assertThat(context.getBeanNamesForAnnotation(org.springframework.web.bind.annotation.RestController.class))
                .hasSizeGreaterThanOrEqualTo(10);
    }
}
