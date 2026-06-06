package com.example.springai.demo.feature06_tools;

import com.example.springai.demo.feature10_advisors.ModelPricing;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
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
    private final StammdatenPlausibilitaetTool stammdatenTool;

    /**
     * {@link EgkCheckTools} und {@link StammdatenPlausibilitaetTool} kommen per DI
     * aus dem Spring-Kontext (beide kapseln den {@link EgkCheckService}).
     * {@link DateTimeTools} hat keinen Zustand und wird einfach direkt instanziiert.
     */
    public ToolCallingController(ChatClient.Builder builder, EgkCheckTools egkCheckTools,
            StammdatenPlausibilitaetTool stammdatenTool) {
        this.chatClient = builder.build();
        this.egkCheckTools = egkCheckTools;
        this.stammdatenTool = stammdatenTool;
    }

    @GetMapping("/api/tools")
    public ToolResponse ask(@RequestParam(defaultValue = "Ist die KVNR A123456780 gueltig?") String message) {
        // Wall-Clock-Messung der gesamten Verarbeitung (inkl. synchron ausgefuehrter Tools).
        long startNanos = System.nanoTime();
        ChatResponse response = chatClient.prompt()
                .user(message)
                // Beide Werkzeug-Sammlungen fuer diese Anfrage anbieten. Das Modell
                // waehlt selbst aus, welches Tool (falls ueberhaupt) es aufruft.
                .tools(egkCheckTools, stammdatenTool, new DateTimeTools())
                .call()
                .chatResponse();
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
        String antwort = response.getResult().getOutput().getText();
        return new ToolResponse(antwort, ToolMetrics.from(usageOf(response), modelOf(response), latencyMs));
    }

    private static Usage usageOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        return response.getMetadata().getUsage();
    }

    private static String modelOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        return response.getMetadata().getModel();
    }

    public record ToolResponse(String antwort, ToolMetrics metrics) {
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
