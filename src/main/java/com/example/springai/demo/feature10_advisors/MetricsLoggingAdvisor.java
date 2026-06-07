package com.example.springai.demo.feature10_advisors;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;


public class MetricsLoggingAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(MetricsLoggingAdvisor.class);

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long startNanos = System.nanoTime();

        // Den Rest der Advisor-Kette (und am Ende das Modell) ausführen.
        ChatClientResponse response = chain.nextCall(request);

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        ChatResponseMetadata metadata = response.chatResponse() != null
                ? response.chatResponse().getMetadata() : null;
        Usage usage = metadata != null ? metadata.getUsage() : null;
        String model = metadata != null ? metadata.getModel() : null;
        Integer inputTokens = usage != null ? usage.getPromptTokens() : null;
        Integer outputTokens = usage != null ? usage.getCompletionTokens() : null;
        Integer totalTokens = usage != null ? usage.getTotalTokens() : null;

        // Kosten aus der Preis-Maptabelle ableiten, sobald Token-Nutzung vorliegt.
        Double costUsd = (inputTokens != null && outputTokens != null)
                ? ModelPricing.costUsd(model, inputTokens, outputTokens) : null;

        log.info("[Metrics] Latenz={}ms, Tokens(in/out/gesamt)={}/{}/{}, Modell={}, Kosten={}",
                elapsedMs, str(inputTokens), str(outputTokens), str(totalTokens),
                model != null ? model : "n/a",
                costUsd != null ? String.format("$%.6f", costUsd) : "n/a");

        // Metriken in den Kontext der Antwort schreiben (immutabler Record -> mutate()).
        Map<String, Object> context = new HashMap<>(response.context());
        context.put("metrics.latencyMs", elapsedMs);
        if (model != null) {
            context.put("metrics.model", model);
        }
        if (inputTokens != null) {
            context.put("metrics.inputTokens", inputTokens);
        }
        if (outputTokens != null) {
            context.put("metrics.outputTokens", outputTokens);
        }
        if (totalTokens != null) {
            context.put("metrics.totalTokens", totalTokens);
        }
        if (costUsd != null) {
            context.put("metrics.costUsd", costUsd);
        }
        return response.mutate().context(context).build();
    }

    private static String str(Integer value) {
        return value != null ? value.toString() : "n/a";
    }

    /** Eindeutiger Name des Advisors (z.B. fürs Logging der Kette). */
    @Override
    public String getName() {
        return "MetricsLoggingAdvisor";
    }

    /**
     * Reihenfolge in der Advisor-Kette. Ein niedriger Wert bedeutet "weiter außen":
     * So umschließt dieser Advisor alle übrigen und misst die echte Gesamtlatenz.
     */
    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }
}
