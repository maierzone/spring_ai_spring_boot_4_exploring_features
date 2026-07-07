package com.example.springai.demo.feature21_exceptiontriage;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * In-Memory-Registry der bisher gesehenen Stoerungen, nach {@code fingerprint}.
 *
 * <p>Zwei Aufgaben in einem, beide atomar ueber {@link #record(String)}:</p>
 * <ul>
 *   <li><b>Dedup:</b> nur beim <em>Erstauftreten</em> eines Fingerprints wird die
 *       (teure, asynchrone) KI-Analyse angestossen – ein Fehler-Sturm derselben
 *       Ursache erzeugt genau <em>einen</em> Modell-Aufruf.</li>
 *   <li><b>Synchroner Cache:</b> sobald die Analyse vorliegt, liefert jeder weitere
 *       Treffer die KI-formulierte {@code userFacingMessage} sofort mit – ohne
 *       Modell-Aufruf.</li>
 * </ul>
 */
@Component
public class ExceptionTriageStore {

    /** Ein Eintrag pro Fingerprint. {@code triage} ist {@code null}, bis die Analyse fertig ist. */
    public static final class Occurrence {
        private final String fingerprint;
        private final Instant firstSeen = Instant.now();
        private final AtomicLong count = new AtomicLong();
        private volatile Instant lastSeen = Instant.now();
        private volatile ExceptionTriage triage;

        private Occurrence(String fingerprint) {
            this.fingerprint = fingerprint;
        }

        public String fingerprint() { return fingerprint; }
        public long count()          { return count.get(); }
        public Instant firstSeen()   { return firstSeen; }
        public Instant lastSeen()    { return lastSeen; }
        public ExceptionTriage triage() { return triage; }
    }

    /** Rueckmeldung von {@link #record(String)}: erstes Auftreten? plus ggf. fertige Analyse. */
    public record Registration(boolean firstOccurrence, ExceptionTriage triage) {
    }

    private final Map<String, Occurrence> byFingerprint = new ConcurrentHashMap<>();

    /**
     * Zaehlt das Auftreten und meldet, ob es das <em>erste</em> war. Nur dann soll
     * der Aufrufer die Analyse anstossen. Die {@code triage} im Ergebnis ist der
     * aktuelle Stand (bei Cache-Hit gefuellt, sonst {@code null}).
     */
    public Registration record(String fingerprint) {
        boolean[] created = {false};
        Occurrence occ = byFingerprint.computeIfAbsent(fingerprint, fp -> {
            created[0] = true;
            return new Occurrence(fp);
        });
        occ.count.incrementAndGet();
        occ.lastSeen = Instant.now();
        return new Registration(created[0], occ.triage);
    }

    /** Traegt das Ergebnis der asynchronen Analyse nach – ab jetzt gibt es Cache-Hits. */
    public void complete(String fingerprint, ExceptionTriage triage) {
        Occurrence occ = byFingerprint.get(fingerprint);
        if (occ != null) {
            occ.triage = triage;
        }
    }

    /** Momentaufnahme fuer das UI: haeufigste zuerst. */
    public List<Occurrence> snapshot() {
        return byFingerprint.values().stream()
                .sorted(Comparator.comparingLong(Occurrence::count).reversed())
                .toList();
    }
}
