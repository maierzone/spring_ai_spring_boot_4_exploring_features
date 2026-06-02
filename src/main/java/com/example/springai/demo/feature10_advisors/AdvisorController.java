package com.example.springai.demo.feature10_advisors;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
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
                        new SimpleLoggerAdvisor(),
                        // Beispielhafte Sperrliste; in echt käme sie aus der Konfiguration.
                        SafeGuardAdvisor.builder()
                                .sensitiveWords(List.of("passwort", "geheim"))
                                .build())
                .build();
    }

    @GetMapping("/api/advisors")
    public String ask(@RequestParam(defaultValue = "Erklaere kurz, was ein Advisor ist.") String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
