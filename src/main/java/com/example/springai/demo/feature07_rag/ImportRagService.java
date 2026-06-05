package com.example.springai.demo.feature07_rag;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * FEATURE 7 (Erweiterung) – Laufzeit-Import eigener Dokumente.
 *
 * <p>Hält einen <b>eigenen, vom Telematik-Wissen getrennten</b>
 * {@link SimpleVectorStore}. Dokumente, die zur Laufzeit importiert werden
 * (per Ordner-Scan oder Upload), landen ausschliesslich hier – die Suche über
 * diesen Store bezieht sich also <em>nur</em> auf die gerade importierten
 * Dateien, nicht auf das vorbefüllte Telematik-Wissen.</p>
 *
 * <p>Unterstützte Formate: {@code .md}/{@code .txt} (Absatz-Split via
 * {@link KnowledgeLoader}) und {@code .pdf} (Seiten via
 * {@link PagePdfDocumentReader}, anschliessend {@link TokenTextSplitter}).
 * Der Store ist In-Memory: Importe leben bis zum Neustart der App.</p>
 */
@Service
public class ImportRagService {

    private static final Set<String> TEXT_EXTENSIONS = Set.of("md", "markdown", "txt", "text");

    private final EmbeddingModel embeddingModel;

    /** Wird bei {@link #clear()} und beim Ordner-Scan durch eine frische Instanz ersetzt. */
    private SimpleVectorStore store;

    /** Namen der aktuell importierten Quellen (für die Stats-Anzeige in der UI). */
    private final List<String> sources = new ArrayList<>();

    /** Gesamtzahl der Dokumente (Chunks) im Import-Store. */
    private int documentCount;

    public ImportRagService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        this.store = newStore();
    }

    private SimpleVectorStore newStore() {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /** Der aktuelle Import-Store – Basis für Ähnlichkeitssuche und RAG-Advisor. */
    public synchronized VectorStore store() {
        return store;
    }

    /** Leert den Import-Speicher (neuer, leerer Store). */
    public synchronized void clear() {
        this.store = newStore();
        this.sources.clear();
        this.documentCount = 0;
    }

    /**
     * Liest alle unterstützten Dateien aus {@code dir} ein und <b>ersetzt</b>
     * damit den bisherigen Import-Bestand (Re-Index der Ordner-Sicht). Existiert
     * der Ordner nicht, wird er angelegt.
     *
     * @return Anzahl erzeugter Dokumente
     */
    public synchronized int importFolder(Path dir) {
        clear();
        try {
            Files.createDirectories(dir);
            try (Stream<Path> entries = Files.list(dir)) {
                List<Path> files = entries
                        .filter(Files::isRegularFile)
                        .filter(p -> isSupported(p.getFileName().toString()))
                        .sorted()
                        .toList();
                for (Path file : files) {
                    addResource(new FileSystemResource(file), file.getFileName().toString());
                }
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException("Import-Ordner nicht lesbar: " + dir, e);
        }
        return documentCount();
    }

    /**
     * Fügt eine hochgeladene Datei (als Byte-Strom) dem Import-Store hinzu, ohne
     * den bisherigen Bestand zu verwerfen.
     *
     * @return Anzahl der aus dieser Datei erzeugten Dokumente
     */
    public synchronized int addUpload(byte[] content, String filename) {
        return addResource(new ByteArrayResource(content), filename);
    }

    /** Namen der aktuell importierten Quellen. */
    public synchronized List<String> sources() {
        return List.copyOf(sources);
    }

    /** Gesamtzahl der Dokumente (Chunks) im Import-Store. */
    public synchronized int documentCount() {
        return documentCount;
    }

    private int addResource(Resource resource, String filename) {
        List<Document> documents = toDocuments(resource, filename);
        if (documents.isEmpty()) {
            return 0;
        }
        store.add(documents);
        sources.add(filename);
        documentCount += documents.size();
        return documents.size();
    }

    /**
     * Zerlegt eine Ressource je nach Dateiendung in Dokumente und versieht sie
     * mit dem Dateinamen als {@code source}-Metadatum.
     */
    static List<Document> toDocuments(Resource resource, String filename) {
        String ext = extension(filename);
        if ("pdf".equals(ext)) {
            List<Document> pages = new PagePdfDocumentReader(resource).read();
            List<Document> chunks = new TokenTextSplitter().apply(pages);
            return chunks.stream()
                    .map(chunk -> Document.builder()
                            .text(chunk.getText())
                            .metadata("source", filename)
                            .build())
                    .toList();
        }
        if (TEXT_EXTENSIONS.contains(ext)) {
            return KnowledgeLoader.loadParagraphs(resource, filename);
        }
        throw new IllegalArgumentException(
                "Nicht unterstützter Dateityp: " + filename + " (erlaubt: .md, .txt, .pdf)");
    }

    private static boolean isSupported(String filename) {
        String ext = extension(filename);
        return "pdf".equals(ext) || TEXT_EXTENSIONS.contains(ext);
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
