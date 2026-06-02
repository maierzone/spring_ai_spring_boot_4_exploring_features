package com.example.springai.demo.feature10_advisors;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;

/**
 * Ein <b>selbst geschriebener</b> Advisor, der die Antwortzeit misst und den
 * Token-Verbrauch protokolliert.
 *
 * <p>Damit wird das eigentliche Erweiterungskonzept von Spring AI greifbar: Ein
 * Advisor implementiert {@link CallAdvisor} und umschließt den restlichen
 * Aufrufketten-Durchlauf. Vor {@code chain.nextCall(...)} kann er die Anfrage
 * inspizieren/verändern, danach die Antwort. Genau so sind auch die eingebauten
 * Advisors (Chat-Memory, RAG, Logging) umgesetzt.</p>
 *
 * <p>Dieser Advisor misst die Latenz des gesamten nachgelagerten Aufrufs, liest –
 * sofern vorhanden – die Token-Nutzung aus den Antwort-Metadaten und legt beide
 * Werte zusätzlich in den Antwort-Kontext, sodass nachgelagerter Code sie
 * auswerten könnte.</p>
 */
public class MetricsLoggingAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(MetricsLoggingAdvisor.class);

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long startNanos = System.nanoTime();

        // Den Rest der Advisor-Kette (und am Ende das Modell) ausführen.
        ChatClientResponse response = chain.nextCall(request);

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        Integer totalTokens = extractTotalTokens(response);

        log.info("[Metrics] Latenz={}ms, Tokens(gesamt)={}", elapsedMs,
                totalTokens != null ? totalTokens : "n/a");

        // Metriken in den Kontext der Antwort schreiben (immutabler Record -> mutate()).
        Map<String, Object> context = new HashMap<>(response.context());
        context.put("metrics.latencyMs", elapsedMs);
        if (totalTokens != null) {
            context.put("metrics.totalTokens", totalTokens);
        }
        return response.mutate().context(context).build();
    }

    /** Liest den Gesamt-Token-Verbrauch aus den Antwort-Metadaten, falls verfügbar. */
    private Integer extractTotalTokens(ChatClientResponse response) {
        if (response.chatResponse() == null || response.chatResponse().getMetadata() == null) {
            return null;
        }
        Usage usage = response.chatResponse().getMetadata().getUsage();
        return usage != null ? usage.getTotalTokens() : null;
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
