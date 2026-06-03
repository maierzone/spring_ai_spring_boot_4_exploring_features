package com.example.springai.demo.feature17_pgvector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.example.springai.demo.embedding.HashingEmbeddingModel;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SimpleVectorStore;

/**
 * Prüft die Controller-Logik von Feature 17 offline gegen einen
 * {@link SimpleVectorStore} (gleiches {@link org.springframework.ai.vectorstore.VectorStore}-
 * Interface wie pgvector, kein Docker nötig). Im Fokus steht der Metadaten-Filter:
 * Die Ähnlichkeitssuche mit {@code category} darf nur Treffer dieser Kategorie
 * liefern.
 */
class PgVectorControllerTest {

    private PgVectorController controllerWithStore() {
        SimpleVectorStore store = SimpleVectorStore.builder(new HashingEmbeddingModel()).build();
        return new PgVectorController(store);
    }

    @Test
    void metadatenFilterSchraenktTrefferAufKategorieEin() {
        PgVectorController controller = controllerWithStore();
        controller.add("Spring AI vereinfacht den Zugriff auf LLMs", "spring");
        controller.add("Der Bundesliga-Spieltag beginnt am Samstag", "sport");

        List<PgVectorController.Hit> hits =
                controller.search("Womit greife ich auf ein Sprachmodell zu?", "spring", 5);

        assertThat(hits).isNotEmpty();
        assertThat(hits).allMatch(hit -> hit.category().equals("spring"));
    }

    @Test
    void ohneFilterWerdenAlleKategorienDurchsucht() {
        PgVectorController controller = controllerWithStore();
        controller.add("Spring AI vereinfacht den Zugriff auf LLMs", "spring");
        controller.add("Der Bundesliga-Spieltag beginnt am Samstag", "sport");

        List<PgVectorController.Hit> hits =
                controller.search("Spieltag", null, 5);

        assertThat(hits).extracting(PgVectorController.Hit::category).contains("sport");
    }
}
