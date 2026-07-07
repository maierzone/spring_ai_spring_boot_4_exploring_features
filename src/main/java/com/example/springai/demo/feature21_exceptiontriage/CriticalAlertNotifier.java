package com.example.springai.demo.feature21_exceptiontriage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Meldet {@link ExceptionTriage.Severity#CRITICAL}-Vorfaelle nach aussen (z.&nbsp;B.
 * Slack/PagerDuty-Webhook). Bewusst schlank und best-effort: ist keine Webhook-URL
 * konfiguriert ({@code app.errors.alert-webhook}), wird nur geloggt – so bleibt die
 * Demo ohne externe Abhaengigkeit lauffaehig, und in Prod genuegt eine Property.
 */
@Component
public class CriticalAlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(CriticalAlertNotifier.class);

    private final String webhookUrl;
    private final RestClient restClient = RestClient.create();

    public CriticalAlertNotifier(@Value("${app.errors.alert-webhook:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public void alert(ExceptionContext ctx, ExceptionTriage triage) {
        String text = "🔴 CRITICAL " + ctx.exceptionType() + " (" + ctx.fingerprint() + ")"
                + " – " + triage.probableRootCause()
                + " | Aktion: " + triage.suggestedAction()
                + " | errorId=" + ctx.errorId();

        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.error("[ALERT] {}", text);
            return;
        }
        try {
            restClient.post().uri(webhookUrl).body(java.util.Map.of("text", text)).retrieve().toBodilessEntity();
        } catch (Exception e) {
            // Alerting darf niemals die Verarbeitung stoeren – nur loggen.
            log.warn("Alert-Webhook fehlgeschlagen: {}", e.toString());
        }
    }
}
