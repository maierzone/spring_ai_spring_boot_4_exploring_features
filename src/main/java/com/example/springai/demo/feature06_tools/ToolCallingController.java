package com.example.springai.demo.feature06_tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FEATURE 6 – Tool / Function Calling.
 *
 * <p>Mit Tools kann das Modell die Grenzen seines Trainingswissens überschreiten:
 * Es darf während der Beantwortung eigene Java-Methoden aufrufen (z.&nbsp;B. um
 * aktuelle Daten, Datenbank- oder API-Ergebnisse zu holen). Hier stellen wir die
 * {@link DateTimeTools} bereit – fragt man nach dem aktuellen Datum, ruft das
 * Modell selbstständig das passende Tool auf.</p>
 *
 * <p>Beispiel: {@code GET /api/tools?message=Welcher Wochentag ist der 2026-12-24?}</p>
 */
@RestController
public class ToolCallingController {

    private final ChatClient chatClient;

    public ToolCallingController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/api/tools")
    public String ask(@RequestParam(defaultValue = "Wie spaet ist es gerade?") String message) {
        return chatClient.prompt()
                .user(message)
                // tools(...) registriert die Werkzeug-Instanz nur für diese Anfrage.
                .tools(new DateTimeTools())
                .call()
                .content();
    }
}
