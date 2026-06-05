package com.example.springai.demo.feature15_gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.springai.demo.feature15_gateway.GatewayController.RouteMetrics;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.DefaultUsage;

/**
 * Stellt sicher, dass der Token-Verbrauch einer Anfrage korrekt aus dem
 * {@link org.springframework.ai.chat.metadata.Usage Usage}-Objekt der ChatResponse
 * in das Badge-Modell ({@link RouteMetrics}) übernommen wird – inklusive des Falls
 * ohne Usage (z.&nbsp;B. ohne API-Key). Reiner Logik-Test, kein Modellaufruf.
 */
class RouteMetricsTest {

    @Test
    void uebernimmtTokenZahlenUndLatenz() {
        RouteMetrics metrics = RouteMetrics.from(new DefaultUsage(120, 45), 318L);

        assertThat(metrics.inputTokens()).isEqualTo(120);
        assertThat(metrics.outputTokens()).isEqualTo(45);
        assertThat(metrics.totalTokens()).isEqualTo(165);
        assertThat(metrics.latencyMs()).isEqualTo(318L);
    }

    @Test
    void ohneUsageNurLatenz() {
        RouteMetrics metrics = RouteMetrics.from(null, 200L);

        assertThat(metrics.inputTokens()).isNull();
        assertThat(metrics.outputTokens()).isNull();
        assertThat(metrics.totalTokens()).isNull();
        assertThat(metrics.latencyMs()).isEqualTo(200L);
    }
}
