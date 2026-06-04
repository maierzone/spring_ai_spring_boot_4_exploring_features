package com.example.springai.demo.feature19_moderation;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FEATURE 19 – Moderation (Content-Safety als Guardrail).
 *
 * <p>Anthropic bietet keinen dedizierten Moderation-Endpunkt (anders als OpenAI/Mistral,
 * fuer die Spring AI ein {@code ModerationModel} autokonfiguriert). Der von Anthropic
 * empfohlene Weg ist, <b>Claude selbst als Klassifikator</b> ueber die normale Chat-API
 * zu nutzen. Genau das verdrahtet dieses Feature – ohne zweiten Provider und mit
 * demselben Anthropic-API-Key wie der Rest der Demo.</p>
 *
 * <ul>
 *   <li>{@code GET /api/moderation?message=…} – ein durch den
 *       {@link ModerationGuardrailAdvisor} geschuetzter Chat: unbedenkliche Eingaben
 *       werden normal beantwortet, problematische bereits vor dem Modellaufruf
 *       abgewiesen, problematische Antworten gefiltert.</li>
 *   <li>{@code GET /api/moderation/check?text=…} – legt das reine Klassifikations-Urteil
 *       offen (Transparenz, analog zu {@code /api/rag/sources}).</li>
 * </ul>
 */
@RestController
public class ModerationController {

    private final ChatClient guardedChatClient;
    private final ContentModerator moderator;

    public ModerationController(ChatClient.Builder builder) {
        // Reihenfolge ist entscheidend: Der Moderator-Client wird OHNE Guardrail gebaut,
        // damit die Klassifikation selbst den Advisor nicht erneut ausloest (Rekursion).
        this.moderator = new ClaudeContentModerator(builder);
        // Erst danach erhaelt der eigentliche Chat-Client den Guardrail-Advisor.
        this.guardedChatClient = builder
                .defaultAdvisors(new ModerationGuardrailAdvisor(moderator))
                .build();
    }

    @GetMapping("/api/moderation")
    public String ask(@RequestParam(defaultValue = "Erklaere kurz, was Content-Moderation ist.") String message) {
        return guardedChatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    @GetMapping("/api/moderation/check")
    public ModerationVerdict check(@RequestParam String text) {
        return moderator.moderate(text);
    }
}
