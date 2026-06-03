package com.example.springai.demo.feature15_gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Reiner Zustands-Test des Routing-Protokolls – ohne Spring-Kontext. */
class RouteRecorderTest {

    @Test
    void istVorErsterWahlNichtAufgeloest() {
        RouteRecorder recorder = new RouteRecorder();
        assertThat(recorder.isResolved()).isFalse();
    }

    @Test
    void haeltRouteUndAntwortFest() {
        RouteRecorder recorder = new RouteRecorder();
        recorder.record("db", "42");
        assertThat(recorder.isResolved()).isTrue();
        assertThat(recorder.route()).isEqualTo("db");
        assertThat(recorder.antwort()).isEqualTo("42");
    }

    @Test
    void letzteWahlGewinnt() {
        RouteRecorder recorder = new RouteRecorder();
        recorder.record("chat", "hallo");
        recorder.record("rag", "antwort");
        assertThat(recorder.route()).isEqualTo("rag");
        assertThat(recorder.antwort()).isEqualTo("antwort");
    }
}
