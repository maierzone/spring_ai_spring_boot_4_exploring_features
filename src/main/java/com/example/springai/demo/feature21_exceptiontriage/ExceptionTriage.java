package com.example.springai.demo.feature21_exceptiontriage;

/**
 * FEATURE 21 – KI-gestuetzte Fehler-Triage (Structured Output am globalen Handler).
 *
 * <p>Zielstruktur der Analyse. Aus diesem Record samt Enums leitet Spring AI ein
 * JSON-Schema ab, an das sich das Modell halten muss (siehe {@link TriageService}).
 * Damit wird aus einer rohen {@link Throwable} eine typisierte, weiterverarbeitbare
 * Einordnung: die {@link #userFacingMessage()} geht an den Client, der Rest
 * ({@link #category()}, {@link #severity()}, {@link #suggestedAction()}) an Logs,
 * Alerting und Statistik.</p>
 *
 * <p>Enums erzwingen – wie schon bei der Ticket-Analyse (Feature 4) – ein
 * geschlossenes Werteset, sodass sich das Ergebnis direkt in Routing-/Alerting-Logik
 * verwenden laesst, ohne String-Vergleiche.</p>
 */
public record ExceptionTriage(
        Category category,
        Severity severity,
        boolean retriable,
        String probableRootCause,
        String suggestedAction,
        String userFacingMessage) {

    /** Fachliche Fehlerklasse – steuert Routing und Interpretation. */
    public enum Category {
        /** Voruebergehend (Timeout, kurzzeitige Ueberlast) – meist wiederholbar. */
        TRANSIENT,
        /** Fehlkonfiguration (fehlende Property, falsche URL, ungueltiger Key). */
        CONFIGURATION,
        /** Programmierfehler (NPE, ungueltiger Zustand) – Code-Fix noetig. */
        PROGRAMMING_BUG,
        /** Ausfall/Fehler eines abhaengigen Systems (DB, Fremd-API, LLM-Provider). */
        EXTERNAL_DEPENDENCY,
        /** Ungueltige Nutzereingabe – eigentlich ein 4xx-Fall. */
        USER_INPUT,
        /** Sicherheitsrelevant (Auth, verdaechtiges Muster). */
        SECURITY
    }

    /** Dringlichkeit – steuert Alerting/Eskalation. */
    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
}
