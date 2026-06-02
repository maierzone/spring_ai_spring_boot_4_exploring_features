package com.example.springai.demo.feature06_tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Die Tool-Methoden sind ganz normale Java-Methoden und lassen sich direkt testen –
 * unabhängig davon, ob ein Modell sie aufruft.
 */
class DateTimeToolsTest {

    private final DateTimeTools tools = new DateTimeTools();

    @Test
    void wochentagWirdKorrektBestimmt() {
        // Der 24.12.2026 ist ein Donnerstag.
        assertThat(tools.dayOfWeek("2026-12-24")).isEqualTo("Donnerstag");
    }

    @Test
    void aktuelleZeitIstParsebar() {
        // Ergebnis muss ein gültiger ISO-Zeitstempel sein (enthält Datum+Zeit-Trenner).
        assertThat(tools.currentDateTime()).contains("T");
    }
}
