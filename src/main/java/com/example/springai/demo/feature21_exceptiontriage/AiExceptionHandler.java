package com.example.springai.demo.feature21_exceptiontriage;

import java.time.Instant;
import java.util.UUID;

import com.example.springai.demo.feature21_exceptiontriage.ExceptionTriageStore.Registration;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * FEATURE 21 – Globaler Exception-Handler mit KI-Triage.
 *
 * <p>Uebersetzt jede unbehandelte Ausnahme in ein RFC-9457-{@link ProblemDetail}
 * und reichert sie – bei Serverfehlern – um eine typisierte KI-Analyse an. Die drei
 * Design-Entscheidungen, auf die es im Produktivbetrieb ankommt:</p>
 *
 * <ol>
 *   <li><b>Der LLM blockiert nie den Antwortpfad.</b> Der Client erhaelt sofort ein
 *       {@link ProblemDetail}; die (langsame) Analyse laeuft asynchron im
 *       {@link ExceptionTriageOrchestrator}.</li>
 *   <li><b>Fehler-Sturm-Schutz durch Fingerprint-Dedup.</b> Pro Fingerabdruck wird
 *       nur <em>einmal</em> analysiert ({@link ExceptionTriageStore}); jede
 *       Wiederholung zaehlt nur hoch – und bekommt, sobald die Analyse vorliegt, die
 *       KI-formulierte Meldung <em>synchron</em> aus dem Cache.</li>
 *   <li><b>4xx bleibt 4xx – ohne KI.</b> Erwartete Client-Fehler (ungueltige Eingabe,
 *       {@link ResponseStatusException}) sind kein Incident: kein Modell-Aufruf,
 *       keine Kosten, kein Rauschen. Nur echte 5xx werden triagiert.</li>
 * </ol>
 *
 * <p>Bewusst mit {@link Ordered#LOWEST_PRECEDENCE} annotiert: existiert im Projekt
 * bereits ein spezifischerer {@code @ControllerAdvice}, hat dieser Vorrang – dieser
 * hier ist die auffangende letzte Instanz.</p>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class AiExceptionHandler {

    private final ExceptionTriageStore store;
    private final ExceptionTriageOrchestrator orchestrator;

    public AiExceptionHandler(ExceptionTriageStore store, ExceptionTriageOrchestrator orchestrator) {
        this.store = store;
        this.orchestrator = orchestrator;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handle(Exception ex, HttpServletRequest request) {
        HttpStatus status = statusFor(ex);
        String errorId = UUID.randomUUID().toString();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, genericDetail(status));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("errorId", errorId);
        problem.setProperty("timestamp", Instant.now().toString());

        // Client-Fehler (4xx) sind erwartetes Verhalten – hier endet die Verarbeitung.
        if (!status.is5xxServerError()) {
            return problem;
        }

        // Ab hier: echter Serverfehler → KI-Triage.
        ExceptionContext ctx = ExceptionContext.capture(ex, errorId, request.getRequestURI(), request.getMethod());
        problem.setProperty("fingerprint", ctx.fingerprint());

        Registration reg = store.record(ctx.fingerprint());
        if (reg.firstOccurrence()) {
            // Erstauftreten: Analyse asynchron anstossen (blockiert die Antwort nicht).
            orchestrator.triageAsync(ctx);
        }
        if (reg.triage() != null) {
            // Cache-Hit: die KI-Analyse liegt bereits vor → sofort mitgeben.
            enrich(problem, reg.triage());
        }
        return problem;
    }

    /** Ueberschreibt die generische Meldung mit der KI-Analyse (nur bei Cache-Hit). */
    private static void enrich(ProblemDetail problem, ExceptionTriage triage) {
        problem.setDetail(triage.userFacingMessage());
        problem.setProperty("category", triage.category());
        problem.setProperty("severity", triage.severity());
        problem.setProperty("retriable", triage.retriable());
    }

    /**
     * Minimale, geschmackvolle Status-Zuordnung. Ein bestehender, projektspezifischer
     * Handler wuerde hier weit mehr Faelle abdecken – fuer die Demo genuegt:
     * {@link ResponseStatusException} traegt ihren Status selbst, ungueltige Argumente
     * sind 400, alles Uebrige ist ein 500.
     */
    private static HttpStatus statusFor(Exception ex) {
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus resolved = HttpStatus.resolve(rse.getStatusCode().value());
            return resolved != null ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (ex instanceof IllegalArgumentException) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * Generische, nicht-leakende Erstmeldung. Interne Details (Exception-Message,
     * Stacktrace) gehen bewusst NIE an den Client – die {@code errorId} verknuepft die
     * Antwort mit der ausfuehrlichen Analyse in Logs/DB.
     */
    private static String genericDetail(HttpStatus status) {
        return status.is5xxServerError()
                ? "Es ist ein interner Fehler aufgetreten."
                : "Die Anfrage konnte nicht verarbeitet werden.";
    }
}
