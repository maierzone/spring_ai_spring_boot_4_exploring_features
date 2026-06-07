package com.example.springai.demo.feature17_ebooks;

import java.util.List;

import com.example.springai.demo.feature16_downloads.DownloadController.ToolMetrics;
import com.example.springai.demo.feature17_ebooks.EbookDownloadService.DownloadOutcome;
import com.example.springai.demo.feature17_ebooks.EbookSearchService.EbookHit;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FEATURE 17 – eBook-Download (Tool Calling Expert, angewandt auf gemeinfreie
 * Buecher).
 *
 * <p>Gleiches "Tool laedt – App stellt bereit"-Muster wie Feature 16, ohne
 * Paywall-Umgehung: Das Modell sucht via {@link EbookTools} ueber Project
 * Gutenberg und Open Library; die Bytes der ausgewaehlten Volltexte holt und
 * speichert der Server. Ausgeliefert werden sie ueber den geteilten
 * {@code /api/downloads/{id}/file}-Endpoint (Feature 16) – die Nutzlast fliesst
 * also am Modell vorbei.</p>
 *
 * <ul>
 *   <li>{@code GET  /api/ebooks/chat}     – LLM-Dispatcher: Titel -&gt; Treffer</li>
 *   <li>{@code POST /api/ebooks/download} – ausgewaehlte eBooks herunterladen</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ebooks")
public class EbookController {

    private final ChatClient chatClient;
    private final EbookTools ebookTools;
    private final EbookSearchRecorder recorder;
    private final EbookDownloadService downloadService;

    public EbookController(ChatClient.Builder builder, EbookTools ebookTools,
            EbookSearchRecorder recorder, EbookDownloadService downloadService) {
        this.chatClient = builder.build();
        this.ebookTools = ebookTools;
        this.recorder = recorder;
        this.downloadService = downloadService;
    }

    /** Schritt 1: Titel verstehen, Such-Tool aufrufen, Treffer + Metriken zurueckgeben. */
    @GetMapping("/chat")
    public ChatResult chat(@RequestParam(defaultValue = "Finde das Buch Faust von Goethe") String message) {
        long startNanos = System.nanoTime();
        ChatResponse response = chatClient.prompt()
                .user(message)
                .tools(ebookTools)
                .call()
                .chatResponse();
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
        String antwort = response.getResult().getOutput().getText();
        return new ChatResult(antwort, recorder.titel(), recorder.hits(),
                ToolMetrics.from(usageOf(response), modelOf(response), latencyMs));
    }

    /** Schritt 2 (ohne LLM): die vom Nutzer ausgewaehlten eBooks herunterladen. */
    @PostMapping("/download")
    public List<DownloadOutcome> download(@RequestBody DownloadRequest request) {
        if (request == null || request.refs() == null) {
            return List.of();
        }
        return request.refs().stream().map(downloadService::download).toList();
    }

    private static Usage usageOf(ChatResponse response) {
        return response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
    }

    private static String modelOf(ChatResponse response) {
        return response == null || response.getMetadata() == null ? null : response.getMetadata().getModel();
    }

    public record DownloadRequest(List<String> refs) {
    }

    public record ChatResult(String antwort, String titel, List<EbookHit> hits, ToolMetrics metrics) {
    }
}
