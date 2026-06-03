package com.example.springai.demo.feature12_observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifiziert die Observability-Verdrahtung ohne Modellaufruf: Liegt Actuator/
 * Micrometer auf dem Klassenpfad, existiert eine {@link MeterRegistry}, und der
 * {@link ObservabilityController} liest daraus die gen_ai-Metriken. Vor dem ersten
 * Modellaufruf ist die Map leer – Hauptsache, die Beans sind sauber verbunden.
 */
@SpringBootTest
class ObservabilityMetricsTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ObservabilityController controller;

    @Test
    void meterRegistryIstVerdrahtet() {
        assertThat(meterRegistry).isNotNull();
    }

    @Test
    void metrikEndpunktLiefertOhneModellaufrufEineMap() {
        // Ohne vorherigen Modellaufruf existieren noch keine gen_ai-Metriken.
        assertThat(controller.genAiMetrics()).isNotNull();
    }
}
