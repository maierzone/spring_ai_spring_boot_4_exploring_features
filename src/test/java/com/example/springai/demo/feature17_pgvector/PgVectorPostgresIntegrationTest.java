package com.example.springai.demo.feature17_pgvector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifiziert den <b>echten</b> pgvector-Pfad gegen eine per Testcontainers
 * hochgefahrene PostgreSQL mit der Extension {@code vector}. Anders als die
 * offline Unit-Tests (H2/SimpleVectorStore) startet dieser Test den vollen
 * Kontext im Profil {@code postgres}, sodass die Spring-AI-Autokonfiguration
 * tatsächlich einen {@code PgVectorStore} verdrahtet.
 *
 * <p>Der Test ist mit {@code @Tag("docker")} markiert und damit aus dem
 * Standard-{@code mvn verify} ausgeschlossen (er braucht eine Docker-Engine).
 * Ausgeführt wird er nur im separaten Workflow {@code ci-docker.yml}.</p>
 *
 * <p>Es wird <b>kein API-Key</b> benötigt: Die Embeddings liefert das offline
 * {@code HashingEmbeddingModel}, ein LLM wird nicht aufgerufen.</p>
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Testcontainers
@Tag("docker")
class PgVectorPostgresIntegrationTest {

    /**
     * Echte pgvector-Datenbank. {@code @ServiceConnection} verdrahtet die
     * {@code DataSource} der Anwendung automatisch auf diesen Container; das
     * docker-compose-Setup (compose.yaml) wird in Tests nicht gestartet.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired
    private VectorStore vectorStore;

    @Test
    void verwendetTatsaechlichDenPgVectorStore() {
        assertThat(vectorStore.getClass().getSimpleName()).isEqualTo("PgVectorStore");
    }

    @Test
    void persistiertDokumenteUndFiltertNachMetadaten() {
        vectorStore.add(List.of(
                Document.builder()
                        .text("Spring AI vereinfacht den Zugriff auf LLMs")
                        .metadata("category", "spring")
                        .build(),
                Document.builder()
                        .text("Der Bundesliga-Spieltag beginnt am Samstag")
                        .metadata("category", "sport")
                        .build()));

        // Ähnlichkeitssuche mit DB-seitigem Metadaten-Filter: nur 'spring'-Treffer.
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("Womit greife ich auf ein Sprachmodell zu?")
                        .topK(5)
                        .filterExpression("category == 'spring'")
                        .build());

        assertThat(hits).isNotEmpty();
        assertThat(hits).allMatch(doc -> "spring".equals(doc.getMetadata().get("category")));
    }
}
