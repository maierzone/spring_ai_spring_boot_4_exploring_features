package com.example.springai.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

/**
 * Verifiziert, dass die statischen Assets der Developer-Konsole vorhanden und
 * korrekt verdrahtet sind. Spring Boot liefert {@code classpath:/static/}
 * automatisch aus – dieser Test sichert ab, dass die Dateien (mit den erwarteten
 * Markern) tatsächlich gepackt werden. Reine Datei-Prüfung, kein Spring-Kontext.
 */
class StaticUiTest {

    private String read(String path) throws Exception {
        return StreamUtils.copyToString(
                new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
    }

    @Test
    void indexSeiteVorhandenUndBindetAssetsEin() throws Exception {
        String html = read("static/index.html");
        assertThat(html)
                .contains("Developer-Konsole")
                .contains("styles.css")
                .contains("app.js")
                // ruft die drei Dev-Feature-Endpunkte auf
                .contains("/api/tickets/analyze");
    }

    @Test
    void javascriptEnthaeltDieKernfunktionen() throws Exception {
        String js = read("static/app.js");
        assertThat(js)
                .contains("analyzeTicket")
                .contains("renderSentimentGauge");
    }
}
