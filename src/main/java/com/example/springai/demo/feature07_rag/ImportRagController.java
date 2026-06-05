package com.example.springai.demo.feature07_rag;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.springai.demo.feature07_rag.RagController.RetrievedSource;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * FEATURE 7 (Erweiterung) – REST-Steuerung für den Laufzeit-Import.
 *
 * <p>Stellt einen dritten Weg neben {@code ask}/{@code sources} bereit: eigene
 * Dokumente zur Laufzeit in einen <b>separaten</b> Vektorspeicher legen und nur
 * darin suchen. Quelle der Dokumente sind entweder ein Server-Ordner
 * ({@code POST /folder}) oder ein Browser-Upload ({@code POST /upload}).</p>
 *
 * <ul>
 *   <li>{@code POST /api/rag/import/folder} – Ordner (neu) einlesen, ersetzt den Bestand.</li>
 *   <li>{@code POST /api/rag/import/upload} – Datei(en) hochladen, zum Bestand hinzufügen.</li>
 *   <li>{@code POST /api/rag/import/clear}  – Import-Speicher leeren.</li>
 *   <li>{@code GET  /api/rag/import/stats}  – Ordnerpfad, Quellnamen, Dokumentzahl.</li>
 *   <li>{@code GET  /api/rag/import/sources} – Ähnlichkeitssuche (ohne LLM).</li>
 *   <li>{@code GET  /api/rag/import/ask}     – RAG-Antwort nur über die Importe (LLM).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/rag/import")
public class ImportRagController {

    private final ImportRagService importService;
    private final ChatClient chatClient;

    /** Server-Ordner, aus dem {@code /folder} liest. Standard: ./rag-import. */
    private final Path importDir;

    public ImportRagController(ImportRagService importService, ChatClient.Builder chatClientBuilder,
            @Value("${demo.rag.import-dir:rag-import}") String importDir) {
        this.importService = importService;
        // Basis-Client ohne festen Advisor: der QuestionAnswerAdvisor wird pro
        // Anfrage angehängt, da er auf den aktuellen Import-Store zeigen muss
        // (der sich bei clear()/folder durch eine neue Instanz ersetzt).
        this.chatClient = chatClientBuilder.build();
        this.importDir = Path.of(importDir).toAbsolutePath().normalize();
    }

    @PostMapping("/folder")
    public Map<String, Object> folder() {
        importService.importFolder(importDir);
        return stats();
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("files") MultipartFile[] files) {
        int added = 0;
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
            try {
                added += importService.addUpload(file.getBytes(), name);
            }
            catch (IOException e) {
                throw new UncheckedIOException("Upload nicht lesbar: " + name, e);
            }
        }
        Map<String, Object> result = stats();
        result.put("added", added);
        return result;
    }

    @PostMapping("/clear")
    public Map<String, Object> clear() {
        importService.clear();
        return stats();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dir", importDir.toString());
        result.put("files", importService.sources());
        result.put("documents", importService.documentCount());
        return result;
    }

    @GetMapping("/sources")
    public List<RetrievedSource> sources(
            @RequestParam(defaultValue = "") String question,
            @RequestParam(defaultValue = "3") int topK) {

        List<Document> hits = importService.store().similaritySearch(
                SearchRequest.builder().query(question).topK(topK).build());

        return hits.stream()
                .map(doc -> new RetrievedSource(
                        doc.getMetadata().getOrDefault("source", "unknown").toString(),
                        doc.getScore(),
                        doc.getText()))
                .toList();
    }

    @GetMapping("/ask")
    public String ask(@RequestParam String question) {
        return chatClient.prompt()
                .user(question)
                .advisors(QuestionAnswerAdvisor.builder(importService.store()).build())
                .call()
                .content();
    }
}
