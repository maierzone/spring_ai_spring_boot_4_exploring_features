package com.example.springai.demo.feature10_advisors;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FEATURE 10 – Advisors (Interzeptoren für die KI-Anfrage).
 *
 * <p>Advisors sind das Erweiterungskonzept von Spring AI: kleine, kombinierbare
 * Bausteine, die sich in den Anfrage-/Antwort-Fluss einklinken – vergleichbar mit
 * Servlet-Filtern. Die zuvor gezeigten Chat-Memory- und RAG-Features sind selbst
 * als Advisors umgesetzt. Hier kombinieren wir zwei eingebaute Advisors:</p>
 * <ul>
 *   <li>{@link MetricsLoggingAdvisor}: <b>selbst geschrieben</b> – misst Latenz
 *       und Token-Verbrauch (zeigt, wie man das Erweiterungskonzept nutzt).</li>
 *   <li>{@link SimpleLoggerAdvisor}: protokolliert Anfrage und Antwort (Debugging).</li>
 *   <li>{@link SafeGuardAdvisor}: blockt Anfragen mit unerwünschten Begriffen ab,
 *       bevor sie überhaupt ans Modell gehen (einfacher Guardrail).</li>
 * </ul>
 *
 * <p>Beispiel: {@code GET /api/advisors?message=Hallo} – enthält die Nachricht
 * einen gesperrten Begriff, antwortet der SafeGuardAdvisor mit einem Hinweis.</p>
 */
@RestController
public class AdvisorController {

    private final ChatClient chatClient;

    public AdvisorController(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultAdvisors(
                        new MetricsLoggingAdvisor(),
                        new SimpleLoggerAdvisor(),
                        // Beispielhafte Sperrliste; in echt käme sie aus der Konfiguration.
                        SafeGuardAdvisor.builder()
                                .sensitiveWords(List.of("passwort", "geheim"))
                                .build())
                .build();
    }

    @GetMapping("/api/advisors")
    public AdvisorResponse ask(@RequestParam(defaultValue = "Erklaere kurz, was ein Advisor ist.") String message) {
        // chatClientResponse() statt content(): liefert zusaetzlich den Antwort-Kontext,
        // in den der MetricsLoggingAdvisor Latenz, Token-Nutzung und Kosten gelegt hat.
        ChatClientResponse response = chatClient.prompt()
                .user(message)
                .call()
                .chatClientResponse();

        String answer = response.chatResponse() != null
                ? response.chatResponse().getResult().getOutput().getText() : "";
        return new AdvisorResponse(answer, Metrics.from(response.context()));
    }

    /** Antwort des Advisor-Features inkl. Metrik-Badge (Tokens, Zeit, Kosten). */
    public record AdvisorResponse(String answer, Metrics metrics) {
    }

    /**
     * Vom {@link MetricsLoggingAdvisor} im Antwort-Kontext abgelegte Kennzahlen.
     * Felder sind {@code null}, wenn kein echter Modellaufruf stattfand (z.&nbsp;B.
     * fehlender API-Key) bzw. das Modell keine Token-Nutzung meldet.
     */
    public record Metrics(Integer inputTokens, Integer outputTokens, Integer totalTokens,
                          Long latencyMs, String model, Double costUsd) {

        static Metrics from(Map<String, Object> context) {
            return new Metrics(
                    (Integer) context.get("metrics.inputTokens"),
                    (Integer) context.get("metrics.outputTokens"),
                    (Integer) context.get("metrics.totalTokens"),
                    (Long) context.get("metrics.latencyMs"),
                    (String) context.get("metrics.model"),
                    (Double) context.get("metrics.costUsd"));
        }
    }
}
