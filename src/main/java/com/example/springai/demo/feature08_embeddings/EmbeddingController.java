package com.example.springai.demo.feature08_embeddings;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class EmbeddingController {

    private final EmbeddingModel embeddingModel;

    public EmbeddingController(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /** Liefert die Dimension des Vektors und die ersten Werte – rein zur Veranschaulichung. */
    @GetMapping("/api/embeddings")
    public EmbeddingInfo embed(@RequestParam(defaultValue = "Spring AI") String text) {
        float[] vector = embeddingModel.embed(text);
        float[] preview = new float[Math.min(8, vector.length)];
        System.arraycopy(vector, 0, preview, 0, preview.length);
        return new EmbeddingInfo(vector.length, preview);
    }

    /** Berechnet die Kosinus-Ähnlichkeit (0..1) zweier Texte über ihre Embeddings. */
    @GetMapping("/api/embeddings/similarity")
    public SimilarityResult similarity(@RequestParam String text1,
                                       @RequestParam String text2) {
        double similarity = cosineSimilarity(
                embeddingModel.embed(text1),
                embeddingModel.embed(text2));
        return new SimilarityResult(text1, text2, similarity);
    }

    /**
     * Kosinus-Ähnlichkeit = Skalarprodukt geteilt durch das Produkt der Längen.
     * Da unsere Vektoren bereits normalisiert sind, genügt im Grunde das
     * Skalarprodukt; die volle Formel ist hier dennoch ausgeschrieben, damit sie
     * auch mit beliebigen (nicht normalisierten) Vektoren korrekt ist.
     */
    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public record EmbeddingInfo(int dimensions, float[] preview) {
    }

    public record SimilarityResult(String text1, String text2, double cosineSimilarity) {
    }
}
