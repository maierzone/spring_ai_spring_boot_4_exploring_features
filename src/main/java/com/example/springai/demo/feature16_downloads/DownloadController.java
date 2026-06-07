package com.example.springai.demo.feature16_downloads;

import java.util.List;

import com.example.springai.demo.feature10_advisors.ModelPricing;
import com.example.springai.demo.feature16_downloads.DownloadRepository.DownloadBytes;
import com.example.springai.demo.feature16_downloads.DownloadRepository.DownloadInfo;
import com.example.springai.demo.feature16_downloads.PaperDownloadService.DownloadOutcome;
import com.example.springai.demo.feature16_downloads.PaperSearchService.PaperHit;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FEATURE 16 – Tool Calling Expert (4. Agentic Pattern).
 *
 * <p>Zeigt das produktive "Tool laedt – App stellt bereit"-Muster ohne Paywall-
 * Umgehung: Das Modell sucht via {@link PaperTools} ueber OpenAlex nach Papern;
 * die Bytes der ausgewaehlten Open-Access-PDFs holt und speichert der Server, und
 * ein ganz normaler REST-Endpoint liefert sie aus – die Nutzlast fliesst also
 * am Modell vorbei.</p>
 *
 * <ul>
 *   <li>{@code GET  /api/downloads/chat}      – LLM-Dispatcher: Thema -> Top-5-Treffer</li>
 *   <li>{@code POST /api/downloads/papers}    – ausgewaehlte OA-Paper herunterladen</li>
 *   <li>{@code GET  /api/downloads}           – Bibliothek der gespeicherten Dateien</li>
 *   <li>{@code GET  /api/downloads/{id}/file} – die rohen Bytes ausliefern</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/downloads")
public class DownloadController {

    private final ChatClient chatClient;
    private final PaperTools paperTools;
    private final PaperSearchRecorder recorder;
    private final PaperDownloadService downloadService;
    private final DownloadRepository repository;

    public DownloadController(ChatClient.Builder builder, PaperTools paperTools,
            PaperSearchRecorder recorder, PaperDownloadService downloadService,
            DownloadRepository repository) {
        this.chatClient = builder.build();
        this.paperTools = paperTools;
        this.recorder = recorder;
        this.downloadService = downloadService;
        this.repository = repository;
    }

    /** Schritt 1: Thema verstehen, Such-Tool aufrufen, Treffer + Metriken zurueckgeben. */
    @GetMapping("/chat")
    public ChatResult chat(@RequestParam(defaultValue = "Finde Paper zum Thema Neurodivergenz bei Tieren") String message) {
        long startNanos = System.nanoTime();
        ChatResponse response = chatClient.prompt()
                .user(message)
                .tools(paperTools)
                .call()
                .chatResponse();
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
        String antwort = response.getResult().getOutput().getText();
        return new ChatResult(antwort, recorder.thema(), recorder.hits(),
                ToolMetrics.from(usageOf(response), modelOf(response), latencyMs));
    }

    /** Schritt 2 (ohne LLM): die vom Nutzer ausgewaehlten OA-Paper herunterladen. */
    @PostMapping("/papers")
    public List<DownloadOutcome> downloadPapers(@RequestBody DownloadRequest request) {
        if (request == null || request.refs() == null) {
            return List.of();
        }
        return request.refs().stream().map(downloadService::download).toList();
    }

    /** Bibliothek: alle gespeicherten Dateien (ohne Bytes). */
    @GetMapping
    public List<DownloadInfo> library() {
        return repository.listAll();
    }

    /** Liefert die rohen Bytes einer gespeicherten Datei aus. */
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable long id) {
        return repository.load(id)
                .map(b -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(b.contentType()))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "inline; filename=\"" + safeFilename(b.title()) + extensionFor(b.contentType()) + "\"")
                        .body(b.data()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Passende Dateiendung zum Content-Type (Default {@code .pdf} fuer Paper-Downloads). */
    private static String extensionFor(String contentType) {
        if (contentType == null) {
            return ".pdf";
        }
        return switch (contentType) {
            case "application/epub+zip" -> ".epub";
            case "text/plain" -> ".txt";
            default -> ".pdf";
        };
    }

    private static String safeFilename(String title) {
        if (title == null || title.isBlank()) {
            return "paper";
        }
        String s = title.replaceAll("[^a-zA-Z0-9-_ ]", "").trim();
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    private static Usage usageOf(ChatResponse response) {
        return response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
    }

    private static String modelOf(ChatResponse response) {
        return response == null || response.getMetadata() == null ? null : response.getMetadata().getModel();
    }

    public record DownloadRequest(List<String> refs) {
    }

    public record ChatResult(String antwort, String thema, List<PaperHit> hits, ToolMetrics metrics) {
    }

    public record ToolMetrics(Integer inputTokens, Integer outputTokens, Integer totalTokens,
                              long latencyMs, Double costUsd) {

        static ToolMetrics from(Usage usage, String model, long latencyMs) {
            if (usage == null) {
                return new ToolMetrics(null, null, null, latencyMs, null);
            }
            Integer inputTokens = usage.getPromptTokens();
            Integer outputTokens = usage.getCompletionTokens();
            Double costUsd = (inputTokens != null && outputTokens != null)
                    ? ModelPricing.costUsd(model, inputTokens, outputTokens) : null;
            return new ToolMetrics(inputTokens, outputTokens, usage.getTotalTokens(), latencyMs, costUsd);
        }
    }
}
