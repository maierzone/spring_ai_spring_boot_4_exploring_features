package com.example.springai.demo.feature18_docsrag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * FEATURE 18 – verarbeitet ein einzelnes Spec-PDF in den {@link VectorStore}.
 *
 * <p>Kapselt den eigentlichen Ingestion-Schritt (PDF → Seiten → Chunks →
 * Embedding → pgvector) als wiederverwendbaren Baustein, den der
 * {@link DocsRagWorker} pro heruntergeladenem Dokument aufruft.</p>
 */
@Component
@Profile("specs")
public class SpecPdfProcessor {

    private final VectorStore vectorStore;

    public SpecPdfProcessor(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Liest das PDF, schneidet es in Chunks, reichert sie mit Spec-Metadaten an
     * und legt sie im Vektorspeicher ab.
     *
     * @return Anzahl gespeicherter Chunks
     */
    public int process(Resource pdf, String spec, String version, String source) {
        List<Document> pages = new PagePdfDocumentReader(pdf).read();
        // Chunk-Groesse an das Embedding-Modell koppeln: paraphrase-multilingual-
        // MiniLM-L12-v2 hat eine maximale Sequenzlaenge von 128 Tokens. Der
        // TokenTextSplitter-Default (800 Tokens) wuerde dazu fuehren, dass das
        // Modell jeden Chunk auf die ersten ~128 Tokens abschneidet und der
        // restliche Text gar nicht ins Embedding einfliesst - die Vektoren
        // repraesentieren dann nur den Chunk-Anfang und die Aehnlichkeitssuche
        // trifft schlecht. Mit 128 Tokens passt jeder Chunk vollstaendig ins
        // Modell-Fenster.
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(128)
                .withMinChunkSizeChars(200)
                .build();
        List<Document> chunks = splitter.apply(pages);

        List<Document> enriched = chunks.stream().map(chunk -> {
            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put("spec", spec);
            metadata.put("version", version);
            metadata.put("source", source);
            return Document.builder().text(chunk.getText()).metadata(metadata).build();
        }).toList();

        vectorStore.add(enriched);
        return enriched.size();
    }
}
