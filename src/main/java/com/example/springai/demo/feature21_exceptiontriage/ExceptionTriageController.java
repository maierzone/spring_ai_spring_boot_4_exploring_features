package com.example.springai.demo.feature21_exceptiontriage;

import java.time.Instant;
import java.util.List;

import com.example.springai.demo.feature21_exceptiontriage.ExceptionTriageRepository.CategoryCount;
import com.example.springai.demo.feature21_exceptiontriage.ExceptionTriageRepository.SeverityCount;
import com.example.springai.demo.feature21_exceptiontriage.ExceptionTriageStore.Occurrence;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Demo-Oberflaeche fuer Feature 21. Die {@code /trigger}-Endpunkte werfen absichtlich
 * verschiedene Ausnahmen, damit man den globalen {@link AiExceptionHandler} live
 * beobachten kann:
 * <ul>
 *   <li>1. Aufruf einer Fehlerart → generische Meldung + {@code errorId}; die KI-Analyse
 *       laeuft asynchron an.</li>
 *   <li>2. Aufruf derselben Fehlerart → Cache-Hit: jetzt kommt die KI-formulierte
 *       {@code userFacingMessage} synchron zurueck.</li>
 * </ul>
 * {@code /live} zeigt den In-Memory-Cache (Dedup in Aktion), {@code /stats} die
 * persistierte Auswertung.
 */
@RestController
public class ExceptionTriageController {

    private final ExceptionTriageStore store;
    private final ExceptionTriageRepository repository;

    public ExceptionTriageController(ExceptionTriageStore store, ExceptionTriageRepository repository) {
        this.store = store;
        this.repository = repository;
    }

    /** Wirft absichtlich eine Ausnahme der gewaehlten Art – landet im globalen Handler. */
    @GetMapping("/api/errors/trigger")
    public String trigger(@RequestParam(defaultValue = "npe") String kind) {
        switch (kind) {
            case "npe" -> {
                String value = null;
                return value.trim(); // NullPointerException
            }
            case "state" -> throw new IllegalStateException(
                    "Verbindungspool erschoepft: keine freie DB-Connection nach 30s.");
            case "arithmetic" -> {
                int x = 1 / zero(); // ArithmeticException
                return String.valueOf(x);
            }
            case "badrequest" -> throw new IllegalArgumentException("Parameter 'kind' ist ungueltig.");
            case "notfound" -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ressource nicht gefunden.");
            default -> throw new RuntimeException("Unbekannte Fehlerart: " + kind);
        }
    }

    /** Live-Sicht auf den Fingerprint-Cache: haeufigste Stoerungen zuerst. */
    @GetMapping("/api/errors/live")
    public List<LiveEntry> live() {
        return store.snapshot().stream().map(LiveEntry::from).toList();
    }

    /** Persistierte Auswertung nach Kategorie und Severity. */
    @GetMapping("/api/errors/stats")
    public Stats stats() {
        return new Stats(repository.countByCategory(), repository.countBySeverity());
    }

    private static int zero() {
        return 0;
    }

    /** Eine Zeile der Live-Sicht (der Store selbst bleibt im UI unsichtbar gekapselt). */
    public record LiveEntry(String fingerprint, long count, Instant firstSeen, Instant lastSeen,
                            boolean analyzed, ExceptionTriage triage) {
        static LiveEntry from(Occurrence occ) {
            return new LiveEntry(occ.fingerprint(), occ.count(), occ.firstSeen(), occ.lastSeen(),
                    occ.triage() != null, occ.triage());
        }
    }

    public record Stats(List<CategoryCount> byCategory, List<SeverityCount> bySeverity) {
    }
}
