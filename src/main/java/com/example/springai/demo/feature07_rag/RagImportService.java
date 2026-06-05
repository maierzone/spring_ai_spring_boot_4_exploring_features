package com.example.springai.demo.feature07_rag;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.springai.demo.embedding.HashingEmbeddingModel;

/**
 * FEATURE 7 – Laufzeit-Import eigener Dokumente in einen <b>getrennten</b>
 * RAG-Speicher.
 *
 * <p>Anders als der vorbefüllte Telematik-Wissensspeicher hält dieser Dienst einen
 * eigenen {@link SimpleVectorStore}, der ausschließlich die zur Laufzeit
 * importierten Dokumente (Ordner-Scan oder Datei-Upload) enthält. So lässt sich
 * RAG mit eigenem Material ausprobieren, ohne den Demo-Wissensspeicher zu
 * vermischen.</p>
 *
 * <p><b>Embedding-Wahl:</b> Standardmäßig wird das Embedding-Modell der Anwendung
 * verwendet (offline {@code HashingEmbeddingModel} bzw. im Profil {@code specs}
 * das ONNX-Modell). Mit {@code demo.rag.import-embedding=onnx} wird – unabhängig
 * vom Profil – ein lokales, multilinguales ONNX-Embedding geladen, das für echte
 * PDFs deutlich bessere (semantische) Treffer liefert.</p>
 */
@Service
public class RagImportService {

    private static final Logger log = LoggerFactory.getLogger(RagImportService.class);

    // Gleiches multilinguales ONNX-Modell wie im Profil 'specs' (Mean-Pooling, 384-dim).
    private static final String ONNX_MODEL_URI =
            "https://huggingface.co/Xenova/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/onnx/model_quantized.onnx";
    private static final String ONNX_TOKENIZER_URI =
            "https://huggingface.co/Xenova/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/tokenizer.json";

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final String embeddingLabel;
    private final Path importDir;

    /** Quell-Dateiname -> Anzahl daraus erzeugter Chunks (für die Statistik). */
    private final Map<String, Integer> sourceChunks = new TreeMap<>();
    private SimpleVectorStore importStore;

    public RagImportService(EmbeddingModel applicationEmbeddingModel,
                            ChatClient.Builder chatClientBuilder,
                            @Value("${demo.rag.import-embedding:default}") String importEmbedding,
                            @Value("${demo.rag.import-dir:rag-import}") String importDir) {
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = selectEmbeddingModel(applicationEmbeddingModel, importEmbedding);
        this.embeddingLabel = labelFor(this.embeddingModel);
        this.importDir = Path.of(importDir);
        this.importStore = newStore();
        log.info("RAG-Import bereit (Embedding={}, Ordner={}).", embeddingLabel, this.importDir.toAbsolutePath());
    }

    // --- öffentliche Operationen (vom Controller aufgerufen) -------------------

    /** Liest alle unterstützten Dateien aus dem Server-Ordner ein (idempotent). */
    public synchronized Map<String, Object> scanFolder() {
        if (!Files.isDirectory(importDir)) {
            return stats();
        }
        List<Path> files;
        try (Stream<Path> stream = Files.list(importDir)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(p -> isSupported(p.getFileName().toString()))
                    .sorted()
                    .toList();
        }
        catch (IOException e) {
            throw new UncheckedIOException("Import-Ordner nicht lesbar: " + importDir, e);
        }
        for (Path file : files) {
            String name = file.getFileName().toString();
            // Bereits importierte Quellen überspringen -> wiederholtes Einlesen ist idempotent.
            if (!sourceChunks.containsKey(name)) {
                ingest(new FileSystemResource(file), name);
            }
        }
        return stats();
    }

    /** Importiert hochgeladene Dateien direkt (ohne Ablage im Server-Ordner). */
    public synchronized Map<String, Object> upload(List<MultipartFile> files) {
        int added = 0;
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename();
            if (name == null || name.isBlank() || !isSupported(name)) {
                continue;
            }
            Path tmp;
            try {
                tmp = Files.createTempFile("rag-import-", "-" + name.replaceAll("[^A-Za-z0-9._-]", "_"));
                Files.write(tmp, file.getBytes());
            }
            catch (IOException e) {
                throw new UncheckedIOException("Upload konnte nicht zwischengespeichert werden: " + name, e);
            }
            try {
                added += ingest(new FileSystemResource(tmp), name);
            }
            finally {
                try {
                    Files.deleteIfExists(tmp);
                }
                catch (IOException ignored) {
                    // Temp-Datei -> beim nächsten Aufräumen des OS spätestens entfernt.
                }
            }
        }
        Map<String, Object> result = stats();
        result.put("added", added);
        return result;
    }

    /** Leert den Import-Speicher vollständig. */
    public synchronized Map<String, Object> clear() {
        importStore = newStore();
        sourceChunks.clear();
        return stats();
    }

    /** RAG-Antwort ausschließlich über die importierten Dokumente. */
    public String ask(String question) {
        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(importStore)
                .searchRequest(SearchRequest.builder().topK(8).build())
                .build();
        return chatClient.prompt().user(question).advisors(advisor).call().content();
    }

    /** Reine Ähnlichkeitssuche über die importierten Dokumente (ohne LLM). */
    public List<ImportedSource> sources(String question, int topK) {
        List<Document> hits = importStore.similaritySearch(
                SearchRequest.builder().query(question).topK(topK).build());
        return hits.stream()
                .map(doc -> new ImportedSource(
                        String.valueOf(doc.getMetadata().getOrDefault("source", "unknown")),
                        doc.getScore(),
                        doc.getText()))
                .toList();
    }

    /** Statusinfo für die UI: Ordner, importierte Quellen, Chunk-Anzahl, Embedding. */
    public synchronized Map<String, Object> stats() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dir", importDir.toAbsolutePath().toString());
        result.put("files", new ArrayList<>(sourceChunks.keySet()));
        result.put("documents", sourceChunks.values().stream().mapToInt(Integer::intValue).sum());
        result.put("embedding", embeddingLabel);
        return result;
    }

    // --- intern ----------------------------------------------------------------

    private int ingest(Resource resource, String filename) {
        List<Document> docs = read(resource, filename);
        if (docs.isEmpty()) {
            return 0;
        }
        importStore.add(docs);
        sourceChunks.merge(filename, docs.size(), Integer::sum);
        return docs.size();
    }

    /** Liest eine Quelle in Chunks und setzt den Originaldateinamen als {@code source}. */
    private List<Document> read(Resource resource, String filename) {
        List<Document> raw;
        if (filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            List<Document> pages = new PagePdfDocumentReader(resource).read();
            // Chunk-Größe an das ONNX-Fenster (128 Tokens) gekoppelt – analog zur Spec-RAG.
            raw = TokenTextSplitter.builder().withChunkSize(128).withMinChunkSizeChars(200).build()
                    .apply(pages);
        }
        else {
            // .md/.txt: ein Absatz = ein Dokument (KnowledgeLoader).
            raw = KnowledgeLoader.loadParagraphs(resource);
        }
        return raw.stream()
                .map(doc -> Document.builder().text(doc.getText()).metadata("source", filename).build())
                .toList();
    }

    private SimpleVectorStore newStore() {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    private static boolean isSupported(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".markdown")
                || lower.endsWith(".txt") || lower.endsWith(".pdf");
    }

    private EmbeddingModel selectEmbeddingModel(EmbeddingModel applicationModel, String importEmbedding) {
        // Nur explizit anfordern, wenn nicht ohnehin schon ein ONNX-Modell aktiv ist (Profil 'specs').
        if ("onnx".equalsIgnoreCase(importEmbedding) && !(applicationModel instanceof TransformersEmbeddingModel)) {
            try {
                TransformersEmbeddingModel onnx = new TransformersEmbeddingModel();
                onnx.setModelResource(ONNX_MODEL_URI);
                onnx.setTokenizerResource(ONNX_TOKENIZER_URI);
                onnx.afterPropertiesSet();
                log.info("RAG-Import lädt lokales ONNX-Embedding (paraphrase-multilingual-MiniLM-L12-v2).");
                return onnx;
            }
            catch (Exception e) {
                log.warn("ONNX-Import-Embedding nicht ladbar ({}), Fallback auf Standard-Embedding.",
                        e.getMessage());
            }
        }
        return applicationModel;
    }

    private static String labelFor(EmbeddingModel model) {
        if (model instanceof TransformersEmbeddingModel) {
            return "onnx";
        }
        if (model instanceof HashingEmbeddingModel) {
            return "hashing";
        }
        return model.getClass().getSimpleName();
    }

    /** Ein importiertes Dokument inkl. Ähnlichkeits-Score und Quelle (für die UI). */
    public record ImportedSource(String source, Double score, String text) {
    }
}
