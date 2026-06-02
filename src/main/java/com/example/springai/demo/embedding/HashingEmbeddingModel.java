package com.example.springai.demo.embedding;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Eine bewusst einfache, <b>deterministische und vollständig offline</b>
 * arbeitende {@link org.springframework.ai.embedding.EmbeddingModel}-Implementierung.
 *
 * <p><b>Warum eine eigene Implementierung?</b><br>
 * Der Anthropic-Provider liefert (Stand Spring AI 2.0.0-M8) kein Embedding-Modell.
 * Für die RAG- und Embedding-Demos bräuchte man sonst einen weiteren Cloud-Provider
 * (mit API-Key) oder ein lokales ONNX-Modell (Download von HuggingFace). Beides
 * würde die CI von Netzwerk/Secrets abhängig machen. Diese Klasse erzeugt
 * Vektoren rein lokal aus dem Text und macht die Demos damit reproduzierbar.</p>
 *
 * <p><b>Verfahren:</b> "Hashing-Trick" / Bag-of-Words. Jeder Token wird auf einen
 * Index im Vektor gehasht und dort hochgezählt. Anschließend wird der Vektor
 * L2-normalisiert, damit das Skalarprodukt zweier Vektoren der Kosinus-Ähnlichkeit
 * entspricht. Das ist kein semantisches Embedding (keine Synonym-Erkennung), reicht
 * für eine nachvollziehbare Demo der Mechanik (Vektor -> Vektorspeicher -> Suche)
 * aber völlig aus.</p>
 *
 * <p>In Produktion würde man hier z.&nbsp;B. {@code spring-ai-starter-model-openai}
 * (OpenAI-Embeddings) oder {@code spring-ai-starter-model-transformers} (lokales
 * ONNX-Modell) einsetzen – der restliche Code bliebe identisch, da gegen das
 * {@code EmbeddingModel}-Interface programmiert wird.</p>
 */
public class HashingEmbeddingModel extends AbstractEmbeddingModel {

    /** Dimension des erzeugten Vektors. 256 ist klein, aber für die Demo ausreichend. */
    private static final int DIMENSIONS = 256;

    /**
     * Kernmethode des {@code EmbeddingModel}-Interfaces: nimmt eine Liste von
     * Eingabetexten entgegen und liefert für jeden Text einen Embedding-Vektor.
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            float[] vector = embedText(inputs.get(i));
            // Der zweite Parameter ist der Index des Ergebnisses innerhalb der Antwort.
            embeddings.add(new Embedding(vector, i));
        }
        return new EmbeddingResponse(embeddings);
    }

    /**
     * Embeddet den Textinhalt eines {@link Document}s (von Spring AI beim Befüllen
     * eines Vektorspeichers aufgerufen).
     */
    @Override
    public float[] embed(Document document) {
        return embedText(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    /**
     * Wandelt einen Text in einen normalisierten Vektor um.
     */
    private float[] embedText(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null || text.isBlank()) {
            return vector;
        }
        // Tokenisierung: kleinschreiben und an allen Nicht-Buchstaben/Ziffern trennen.
        for (String token : text.toLowerCase().split("[^\\p{L}\\p{Nd}]+")) {
            if (token.isBlank()) {
                continue;
            }
            // Token-Hash auf einen positiven Vektor-Index abbilden.
            int index = Math.floorMod(token.hashCode(), DIMENSIONS);
            vector[index] += 1.0f;
        }
        return l2Normalize(vector);
    }

    /**
     * L2-Normalisierung: teilt den Vektor durch seine Länge. Danach hat er Länge 1,
     * sodass das Skalarprodukt zweier Vektoren direkt die Kosinus-Ähnlichkeit ergibt.
     */
    private float[] l2Normalize(float[] vector) {
        double sumOfSquares = 0.0;
        for (float v : vector) {
            sumOfSquares += v * v;
        }
        double norm = Math.sqrt(sumOfSquares);
        if (norm == 0.0) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }
}
