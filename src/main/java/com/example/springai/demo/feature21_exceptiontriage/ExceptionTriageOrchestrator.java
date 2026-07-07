package com.example.springai.demo.feature21_exceptiontriage;

import com.example.springai.demo.feature21_exceptiontriage.ExceptionTriage.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Fuehrt die (langsame) Fehler-Analyse <em>ausserhalb</em> des Request-Threads aus
 * und verkabelt die Nachverarbeitung: Cache fuellen, persistieren, ggf. alarmieren.
 *
 * <p><b>Warum eine eigene Bean?</b> {@link Async} wirkt nur ueber den Spring-Proxy,
 * also nur bei einem Aufruf von <em>aussen</em>. Der {@code AiExceptionHandler} ruft
 * deshalb diese Bean auf – so laeuft {@link #triageAsync} garantiert im
 * {@code triageExecutor}-Pool und blockiert nie die Fehlerantwort an den Client.</p>
 *
 * <p>Weil die Analyse auf einem eigenen Thread laeuft (kein Web-Request), kann eine
 * hier auftretende Ausnahme den globalen {@code @RestControllerAdvice} nicht erneut
 * ausloesen – zusammen mit dem Fallback im {@link TriageService} ist eine
 * Rekursion damit ausgeschlossen. Trotzdem wird defensiv alles gekapselt.</p>
 */
@Component
public class ExceptionTriageOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ExceptionTriageOrchestrator.class);

    private final TriageService triageService;
    private final ExceptionTriageStore store;
    private final ExceptionTriageRepository repository;
    private final CriticalAlertNotifier notifier;

    public ExceptionTriageOrchestrator(TriageService triageService,
                                       ExceptionTriageStore store,
                                       ExceptionTriageRepository repository,
                                       CriticalAlertNotifier notifier) {
        this.triageService = triageService;
        this.store = store;
        this.repository = repository;
        this.notifier = notifier;
    }

    @Async("triageExecutor")
    public void triageAsync(ExceptionContext ctx) {
        try {
            ExceptionTriage triage = triageService.triage(ctx);
            // Ab jetzt liefert der Store diesen Fingerprint synchron mit KI-Meldung aus.
            store.complete(ctx.fingerprint(), triage);
            persistQuietly(ctx, triage);
            if (triage.severity() == Severity.CRITICAL) {
                notifier.alert(ctx, triage);
            }
        } catch (Exception e) {
            // Letzte Sicherheitsleine des Async-Threads – nichts darf hier entkommen.
            log.warn("Nachverarbeitung der Triage {} fehlgeschlagen: {}", ctx.fingerprint(), e.toString());
        }
    }

    /** Persistenz ist best-effort: eine DB-Panne soll Cache/Alerting nicht verhindern. */
    private void persistQuietly(ExceptionContext ctx, ExceptionTriage triage) {
        try {
            repository.save(ctx, triage);
        } catch (Exception e) {
            log.warn("Persistenz der Triage {} fehlgeschlagen: {}", ctx.fingerprint(), e.toString());
        }
    }
}
