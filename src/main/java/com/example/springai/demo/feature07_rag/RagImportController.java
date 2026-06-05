package com.example.springai.demo.feature07_rag;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.springai.demo.feature07_rag.RagImportService.ImportedSource;

/**
 * FEATURE 7 – REST-Steuerung für den Laufzeit-Dokument-Import (getrennter Store).
 *
 * <ul>
 *   <li>{@code POST /api/rag/import/folder} – unterstützte Dateien aus dem
 *       Server-Ordner einlesen.</li>
 *   <li>{@code POST /api/rag/import/upload} – Datei(en) direkt hochladen
 *       (Multipart-Feld {@code files}).</li>
 *   <li>{@code POST /api/rag/import/clear}  – Import-Speicher leeren.</li>
 *   <li>{@code GET  /api/rag/import/ask}    – RAG-Antwort über die Importe.</li>
 *   <li>{@code GET  /api/rag/import/sources}– Ähnlichkeitssuche über die Importe.</li>
 * </ul>
 *
 * <p>Die eigentliche Logik (eigener Vektorspeicher, Ingestion, Embedding-Wahl)
 * liegt im {@link RagImportService}; der Controller bleibt eine dünne Hülle.</p>
 */
@RestController
@RequestMapping("/api/rag/import")
public class RagImportController {

    private final RagImportService service;

    public RagImportController(RagImportService service) {
        this.service = service;
    }

    @PostMapping("/folder")
    public Map<String, Object> folder() {
        return service.scanFolder();
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("files") List<MultipartFile> files) {
        return service.upload(files);
    }

    @PostMapping("/clear")
    public Map<String, Object> clear() {
        return service.clear();
    }

    @GetMapping("/ask")
    public String ask(@RequestParam String question) {
        return service.ask(question);
    }

    @GetMapping("/sources")
    public List<ImportedSource> sources(@RequestParam String question,
                                        @RequestParam(defaultValue = "8") int topK) {
        return service.sources(question, topK);
    }
}
