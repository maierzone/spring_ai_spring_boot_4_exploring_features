package com.example.springai.demo.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Reine Logik-Tests für das offline-Embedding-Modell – kein Netzwerk, kein Spring-Kontext.
 */
class HashingEmbeddingModelTest {

    private final HashingEmbeddingModel model = new HashingEmbeddingModel();

    @Test
    void erzeugtVektorInKonfigurierterDimension() {
        float[] vector = model.embed("Spring AI ist klasse");
        assertThat(vector).hasSize(256);
    }

    @Test
    void identischerTextErgibtIdentischenVektor() {
        // Determinismus ist die Voraussetzung dafür, dass die Demo reproduzierbar ist.
        assertThat(model.embed("Hallo Welt")).isEqualTo(model.embed("Hallo Welt"));
    }

    @Test
    void vektorIstL2Normalisiert() {
        float[] vector = model.embed("Tokenisierung und Normalisierung");
        double sumOfSquares = 0.0;
        for (float v : vector) {
            sumOfSquares += v * v;
        }
        // Länge eines normalisierten Vektors ist 1 (Toleranz wegen float-Rundung).
        assertThat(Math.sqrt(sumOfSquares)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void leererTextErgibtNullvektor() {
        assertThat(model.embed("   ")).containsOnly(0.0f);
    }
}
