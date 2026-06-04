package com.example.springai.demo.feature06_tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FEATURE 6 – Tool / Function Calling.
 *
 * <p>Mit Tools kann das Modell die Grenzen seines Trainingswissens ueberschreiten:
 * Es darf waehrend der Beantwortung eigene Java-Methoden aufrufen (z.&nbsp;B. um
 * Datenbank- oder API-Ergebnisse zu holen). Hier bekommt es die
 * {@link EgkCheckTools} – fachliche Einzelsatz-Pruefungen rund um die eGK
 * (KVNR-/Luhn-Pruefziffer, Kartenstatus, ICD-10-Aufloesung, Zertifikats-Gueltigkeit)
 * – sowie die {@link DateTimeTools} fuer Datum/Wochentag. Das Modell waehlt
 * selbststaendig das passende Tool und dessen Argumente.</p>
 *
 * <p>Abgrenzung zu Feature 14: dort beantwortet das Modell Aggregations-Fragen
 * ("wie viele", "Verteilung"); hier geht es um genau einen Satz/eine Nummer.</p>
 *
 * <p>Beispiel: {@code GET /api/tools?message=Ist die KVNR A123456780 gueltig?}</p>
 */
@RestController
public class ToolCallingController {

    private final ChatClient chatClient;
    private final EgkCheckTools egkCheckTools;

    /**
     * {@link EgkCheckTools} kommt per DI aus dem Spring-Kontext (kapselt den
     * {@link EgkCheckService}). {@link DateTimeTools} hat keinen Zustand und wird
     * einfach direkt instanziiert.
     */
    public ToolCallingController(ChatClient.Builder builder, EgkCheckTools egkCheckTools) {
        this.chatClient = builder.build();
        this.egkCheckTools = egkCheckTools;
    }

    @GetMapping("/api/tools")
    public String ask(@RequestParam(defaultValue = "Ist die KVNR A123456780 gueltig?") String message) {
        return chatClient.prompt()
                .user(message)
                // Beide Werkzeug-Sammlungen fuer diese Anfrage anbieten. Das Modell
                // waehlt selbst aus, welches Tool (falls ueberhaupt) es aufruft.
                .tools(egkCheckTools, new DateTimeTools())
                .call()
                .content();
    }
}
