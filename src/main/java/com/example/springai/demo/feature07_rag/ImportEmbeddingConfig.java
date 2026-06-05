package com.example.springai.demo.feature07_rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FEATURE 7 (Erweiterung) – Embedding-Modell für den Laufzeit-Import.
 *
 * <p>Standardmäßig nutzt der Import-Store dasselbe Modell wie der Rest der App
 * (offline {@code HashingEmbeddingModel} im Default-Profil, ONNX unter {@code specs}).
 * Das offline Hashing-Modell ist bewusst <b>nicht semantisch</b> (256-dim Bag-of-Words)
 * und liefert in echten, langen PDFs schlechte Treffer.</p>
 *
 * <p>Mit {@code demo.rag.import-embedding=onnx} bekommt <b>nur</b> der Import-Store ein
 * echtes, lokal in-process laufendes ONNX-Embedding (multilinguales all-MiniLM, 384-dim) –
 * nötig für brauchbare semantische Suche in importierten PDFs (auch deutsch). Das Modell
 * (~113&nbsp;MB) wird einmalig geladen und lokal gecacht; der Offline-Default (und damit
 * CI/Tests) bleibt unberührt.</p>
 *
 * <p>{@code defaultCandidate = false}: dieser zweite {@link EmbeddingModel} ist nur über den
 * Qualifier {@code importEmbeddingModel} injizierbar und stört die unqualifizierten
 * Injektionen (Vektorspeicher, Embedding-Feature) nicht.</p>
 */
@Configuration
public class ImportEmbeddingConfig {

    // Gleiche multilinguale Modell-/Tokenizer-Quellen wie das Profil 'specs'
    // (paraphrase-multilingual-MiniLM-L12-v2, quantisiert) – trifft deutschen
    // Fachtext deutlich besser als das englisch-zentrierte Standardmodell.
    private static final String DEFAULT_MODEL_URI =
            "https://huggingface.co/Xenova/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/onnx/model_quantized.onnx";
    private static final String DEFAULT_TOKENIZER_URI =
            "https://huggingface.co/Xenova/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/tokenizer.json";

    @Bean(defaultCandidate = false)
    EmbeddingModel importEmbeddingModel(
            EmbeddingModel primaryEmbeddingModel,
            @Value("${demo.rag.import-embedding:default}") String mode,
            @Value("${spring.ai.embedding.transformer.onnx.model-uri:" + DEFAULT_MODEL_URI + "}") String modelUri,
            @Value("${spring.ai.embedding.transformer.tokenizer.uri:" + DEFAULT_TOKENIZER_URI + "}") String tokenizerUri) {

        if (!"onnx".equalsIgnoreCase(mode)) {
            // Default: dasselbe Modell wie der Rest der App (offline bzw. specs-ONNX).
            return primaryEmbeddingModel;
        }

        // Identisch zur specs-Autokonfiguration: nur Modell- und Tokenizer-Quelle setzen,
        // alle übrigen Felder (leere Tokenizer-Optionen, modelOutputName=last_hidden_state,
        // Cache-Verzeichnis) bleiben auf den Klassen-Defaults.
        TransformersEmbeddingModel model = new TransformersEmbeddingModel();
        model.setModelResource(modelUri);
        model.setTokenizerResource(tokenizerUri);
        try {
            model.afterPropertiesSet();
        }
        catch (Exception e) {
            throw new IllegalStateException(
                    "ONNX-Import-Embedding konnte nicht initialisiert werden (Download/Modell prüfen): "
                            + e.getMessage(), e);
        }
        return model;
    }
}
