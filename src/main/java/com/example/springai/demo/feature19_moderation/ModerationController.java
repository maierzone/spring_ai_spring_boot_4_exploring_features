package com.example.springai.demo.feature19_moderation;

import com.example.springai.demo.feature10_advisors.ModelPricing;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class ModerationController {

    private final ChatClient guardedChatClient;
    private final ContentModerator moderator;

    public ModerationController(ChatClient.Builder builder) {
        // Der Moderator-Client muss OHNE Guardrail bleiben, sonst wuerde die Klassifikation
        // den Advisor erneut ausloesen (Endlosrekursion). Die Bau-Reihenfolge allein reicht
        // dafuer NICHT: Spring AIs Builder.build() reicht dieselbe mutable Request-Spec an den
        // gebauten Client weiter, ohne sie zu kopieren – ein spaeteres defaultAdvisors(...)
        // wuerde also auch in den bereits gebauten Moderator-Client durchschlagen. Deshalb
        // erhaelt der Guardrail-Client eine eigene, unabhaengige Builder-Kopie (clone()).
        this.moderator = new ClaudeContentModerator(builder);
        this.guardedChatClient = builder.clone()
                .defaultAdvisors(new ModerationGuardrailAdvisor(moderator))
                .build();
    }

    @GetMapping("/api/moderation")
    public ModerationResponse ask(@RequestParam(defaultValue = "Erklaere kurz, was Content-Moderation ist.") String message) {
        long startNanos = System.nanoTime();
        ChatResponse response = guardedChatClient.prompt()
                .user(message)
                .call()
                .chatResponse();
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
        String antwort = (response != null && response.getResult() != null)
                ? response.getResult().getOutput().getText() : "";
        return new ModerationResponse(antwort, ModerationMetrics.from(usageOf(response), modelOf(response), latencyMs));
    }

    @GetMapping("/api/moderation/check")
    public ModerationVerdict check(@RequestParam String text) {
        return moderator.moderate(text);
    }

    /** Token-Verbrauch aus den Antwort-Metadaten lesen, sofern vorhanden. */
    private static Usage usageOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        return response.getMetadata().getUsage();
    }

    /** Modell-Id aus den Antwort-Metadaten lesen (fuer die Kostenabschaetzung). */
    private static String modelOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        return response.getMetadata().getModel();
    }

    /**
     * Antwort des geschuetzten Chats: die (ggf. gefilterte) Modell-Antwort plus die
     * Ressourcen-Metriken. Hinweis: gezaehlt wird der Haupt-Chat-Aufruf; die internen
     * Klassifikations-Aufrufe des Guardrail-Advisors fliessen hier nicht mit ein.
     */
    public record ModerationResponse(String antwort, ModerationMetrics metrics) {
    }

    /** Ressourcenverbrauch fuer die Badge-Anzeige (gleiche Felder wie bei den uebrigen Features). */
    public record ModerationMetrics(Integer inputTokens, Integer outputTokens, Integer totalTokens,
                                    long latencyMs, Double costUsd) {

        static ModerationMetrics from(Usage usage, String model, long latencyMs) {
            if (usage == null) {
                return new ModerationMetrics(null, null, null, latencyMs, null);
            }
            Integer inputTokens = usage.getPromptTokens();
            Integer outputTokens = usage.getCompletionTokens();
            Double costUsd = (inputTokens != null && outputTokens != null)
                    ? ModelPricing.costUsd(model, inputTokens, outputTokens) : null;
            return new ModerationMetrics(inputTokens, outputTokens, usage.getTotalTokens(), latencyMs, costUsd);
        }
    }
}
