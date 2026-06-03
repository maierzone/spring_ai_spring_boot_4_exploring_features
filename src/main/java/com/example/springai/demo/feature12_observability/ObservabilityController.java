package com.example.springai.demo.feature12_observability;

import java.util.LinkedHashMap;
import java.util.Map;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FEATURE 12 – Observability (Micrometer-Metriken &amp; Tracing).
 *
 * <p>Sobald {@code spring-boot-starter-actuator} (und damit Micrometer) auf dem
 * Klassenpfad liegt, instrumentiert Spring AI seine Aufrufe automatisch:</p>
 * <ul>
 *   <li><b>Metriken</b> wie {@code gen_ai.client.token.usage} (Input-/Output-/Gesamt-
 *       Tokens) und {@code gen_ai.client.operation} (Latenz-Timer) – pro Modell und
 *       Operation getaggt.</li>
 *   <li><b>Observations/Tracing</b> {@code spring.ai.chat.client} um jeden
 *       {@code call()}/{@code stream()} (inkl. verwendeter Advisors, Tool-Namen,
 *       Conversation-ID).</li>
 * </ul>
 *
 * <p>Der eigentliche Mehrwert: dieselben Token-/Latenz-Zahlen, die der
 * selbstgebaute {@code MetricsLoggingAdvisor} (Feature 10) nur ins Log schreibt,
 * landen hier <em>standardisiert</em> in der Metrik-Registry und sind unter
 * {@code /actuator/metrics} sowie via Prometheus/Grafana auswertbar.</p>
 *
 * <p>Beispiele:
 * {@code GET /api/observability/ask?message=...} (loest einen Modellaufruf aus) und
 * {@code GET /api/observability/metrics} (zeigt die bereits erfassten gen_ai-Metriken).</p>
 */
@RestController
public class ObservabilityController {

    private final ChatClient chatClient;
    private final MeterRegistry meterRegistry;

    public ObservabilityController(ChatClient.Builder builder, MeterRegistry meterRegistry) {
        this.chatClient = builder.build();
        this.meterRegistry = meterRegistry;
    }

    @GetMapping("/api/observability/ask")
    public String ask(@RequestParam(defaultValue = "Nenne drei Vorteile von Observability.") String message) {
        // Ein ganz normaler Aufruf - die Instrumentierung passiert transparent im Hintergrund.
        return chatClient.prompt().user(message).call().content();
    }

    /**
     * Liest die von Spring AI erzeugten {@code gen_ai.*}-Metriken direkt aus der
     * Micrometer-Registry. Vor dem ersten Modellaufruf ist die Liste leer – das ist
     * erwartbar und macht den Endpunkt offline/ohne API-Key testbar.
     */
    @GetMapping("/api/observability/metrics")
    public Map<String, Double> genAiMetrics() {
        Map<String, Double> result = new LinkedHashMap<>();
        for (Meter meter : meterRegistry.getMeters()) {
            String name = meter.getId().getName();
            if (name.startsWith("gen_ai") || name.startsWith("spring.ai")) {
                for (Measurement m : meter.measure()) {
                    result.put(name + " [" + m.getStatistic() + "]", m.getValue());
                }
            }
        }
        return result;
    }
}
