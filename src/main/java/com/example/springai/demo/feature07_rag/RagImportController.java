package com.example.springai.demo.feature07_rag;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/api/rag/import")
@Profile("specs | postgres")
public class RagImportController {

    private final RagImportStore store;
    private final ChatClient chatClient;

    public RagImportController(RagImportStore store, ChatClient.Builder builder) {
        this.store = store;
        this.chatClient = builder.build();
    }

    /** Liest unterstützte Dateien aus dem Server-Import-Ordner ein. */
    @PostMapping("/folder")
    public Map<String, Object> folder() {
        return store.ingestFolder();
    }

    /** Verarbeitet direkt hochgeladene Dateien (Form-Feld {@code files}). */
    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("files") MultipartFile[] files) {
        return store.ingestUploads(files);
    }

    /** Leert den Import-Speicher. */
    @PostMapping("/clear")
    public Map<String, Object> clear() {
        return store.clear();
    }

    /**
     * Transparenz-Endpunkt: zeigt die lexikalischen Treffer (mit Score) zur Frage,
     * ohne LLM/API-Key. Exakt-/Phrasen-Treffer stehen oben.
     */
    @GetMapping("/sources")
    public List<RagImportStore.Hit> sources(@RequestParam String question,
                                            @RequestParam(defaultValue = "8") int topK) {
        return store.search(question, topK);
    }

    /**
     * RAG-Antwort ausschließlich über die importierten Dokumente: holt die besten
     * lexikalischen Treffer und hängt sie als Kontext an den Prompt.
     */
    @GetMapping("/ask")
    public String ask(@RequestParam String question) {
        List<RagImportStore.Hit> hits = store.search(question, 8);
        if (hits.isEmpty()) {
            return "Keine passenden Stellen in den importierten Dokumenten gefunden.";
        }
        String context = hits.stream()
                .map(h -> "- (" + h.source() + ") " + h.text())
                .collect(Collectors.joining("\n"));
        return chatClient.prompt()
                .system("Beantworte die Frage ausschliesslich anhand des folgenden Kontexts. "
                        + "Wenn die Antwort dort nicht steht, sage das offen.\n\nKontext:\n" + context)
                .user(question)
                .call()
                .content();
    }
}
