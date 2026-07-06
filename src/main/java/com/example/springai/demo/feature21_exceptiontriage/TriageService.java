package com.example.springai.demo.feature21_exceptiontriage;

import com.example.springai.demo.feature21_exceptiontriage.ExceptionTriage.Category;
import com.example.springai.demo.feature21_exceptiontriage.ExceptionTriage.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

/**
 * Fuehrt die eigentliche KI-Analyse einer Exception durch – Structured Output
 * (Feature 4) angewandt auf {@link Throwable} statt auf ein Support-Ticket.
 *
 * <p>Zwei Eigenschaften sind fuer den Produktivbetrieb entscheidend:</p>
 * <ol>
 *   <li><b>Rekursionsschutz &amp; Graceful Degradation:</b> Schlaegt der Modell-Aufruf
 *       selbst fehl (Provider down, Rate-Limit, ungueltiges JSON), wird der Fehler
 *       <em>niemals</em> erneut triagiert – ein deterministischer {@link #fallback}
 *       genuegt. So kann die Fehler-Triage nicht ihrerseits einen Fehler-Sturm ausloesen.</li>
 *   <li><b>Eigener ChatClient:</b> aus einer unabhaengigen Builder-Instanz gebaut,
 *       ganz ohne Advisors – die Triage soll nicht durch Guardrails/Memory anderer
 *       Features beeinflusst werden.</li>
 * </ol>
 */
@Service
public class TriageService {

    private static final Logger log = LoggerFactory.getLogger(TriageService.class);

    private static final String PROMPT = """
            Du bist ein Site-Reliability-Assistent. Analysiere die folgende Java-Exception
            aus einer Spring-Boot-Anwendung und fuelle die Triage-Felder aus.
            - probableRootCause: ein Satz zur wahrscheinlichen Ursache.
            - suggestedAction: konkreter naechster Schritt fuer die/den On-Call.
            - userFacingMessage: eine kurze, laienverstaendliche, nicht-technische Meldung
              fuer Endnutzer (keine Stacktraces, keine internen Details).

            Exception:
            {exception}

            {format}
            """;

    private final ChatClient chatClient;

    public TriageService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /** Analysiert die Exception oder liefert bei Meta-Fehlern einen sicheren Fallback. */
    public ExceptionTriage triage(ExceptionContext ctx) {
        BeanOutputConverter<ExceptionTriage> converter = new BeanOutputConverter<>(ExceptionTriage.class);
        try {
            String text = chatClient.prompt()
                    .user(u -> u.text(PROMPT)
                            .param("exception", ctx.summary())
                            .param("format", converter.getFormat()))
                    .call()
                    .content();
            ExceptionTriage triage = converter.convert(text);
            return triage != null ? triage : fallback();
        } catch (Exception e) {
            // Meta-Fehler: die Analyse selbst schlug fehl. Bewusst nur loggen und
            // degradieren – kein erneuter Modell-Aufruf, keine Weitergabe.
            log.warn("KI-Triage fuer {} fehlgeschlagen: {}", ctx.fingerprint(), e.toString());
            return fallback();
        }
    }

    /** Deterministische Ersatz-Triage, wenn keine KI-Analyse verfuegbar ist. */
    private static ExceptionTriage fallback() {
        return new ExceptionTriage(
                Category.PROGRAMMING_BUG,
                Severity.MEDIUM,
                false,
                "Automatische Analyse nicht verfuegbar.",
                "Manuelle Pruefung erforderlich – siehe Logs anhand der errorId.",
                "Es ist ein unerwarteter Fehler aufgetreten. Bitte versuchen Sie es spaeter erneut.");
    }
}
