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
                .contains("Exploration")
                .contains("styles.css")
                .contains("app.js")
                // ruft die Dev-Feature-Endpunkte auf
                .contains("/api/tickets/analyze")
                .contains("/api/db/ask");
    }

    @Test
    void javascriptEnthaeltDieKernfunktionen() throws Exception {
        String js = read("static/app.js");
        assertThat(js)
                .contains("analyzeTicket")
                .contains("renderSentimentGauge")
                .contains("askDb");
    }

    @Test
    void docsRagPanelIstVerdrahtet() throws Exception {
        String panels = read("static/panels.jsx");
        assertThat(panels)
                .contains("DocsRagPanel")
                .contains("/api/docs-rag/stats")
                .contains("/api/docs-rag/seed")
                .contains("/api/docs-rag/start")
                .contains("/api/docs-rag/stop")
                // im PANELS-Register eingetragen
                .contains("docsrag: DocsRagPanel");
        // im Navigations-Menue registriert
        assertThat(read("static/components.jsx")).contains("Exploration");
    }

    @Test
    void fehlerTriagePanelIstVerdrahtet() throws Exception {
        String panels = read("static/panels.jsx");
        assertThat(panels)
                .contains("ErrorTriagePanel")
                .contains("/api/errors/trigger")
                .contains("/api/errors/live")
                .contains("/api/errors/stats")
                // im PANELS-Register eingetragen
                .contains("errortriage: ErrorTriagePanel");
        // im Navigations-Menue registriert
        assertThat(read("static/components.jsx")).contains("errortriage");
    }
}
