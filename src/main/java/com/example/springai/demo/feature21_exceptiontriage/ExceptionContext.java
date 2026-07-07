package com.example.springai.demo.feature21_exceptiontriage;

/**
 * Sicherer, LLM-tauglicher Schnappschuss einer Exception.
 *
 * <p>Bewusst <em>nicht</em> die {@link Throwable} selbst herumreichen: Was an das
 * Modell (und spaeter in die DB) geht, wird hier auf das Noetige reduziert –
 * Exception-Typ, Meldung und ein <em>gekuerzter</em> Stacktrace. Request-Parameter
 * und -Bodies bleiben aussen vor; sie koennen personenbezogene Daten (z.&nbsp;B.
 * eGK-Daten) oder Secrets enthalten. Genau hier waere der Andockpunkt fuer eine
 * zusaetzliche Redaction-Stufe.</p>
 */
public record ExceptionContext(
        String errorId,
        String fingerprint,
        String exceptionType,
        String path,
        String httpMethod,
        String summary) {

    /** Wie viele Stackframes maximal an das Modell gehen. */
    private static final int MAX_FRAMES = 8;

    /**
     * Baut aus einer Exception den sicheren Kontext. Der {@code fingerprint} wird
     * hier zentral mit-berechnet ({@link ExceptionFingerprint}), damit es genau eine
     * Erfassungsstelle gibt.
     */
    public static ExceptionContext capture(Throwable ex, String errorId, String path, String httpMethod) {
        String fingerprint = ExceptionFingerprint.of(ex);
        return new ExceptionContext(
                errorId,
                fingerprint,
                ex.getClass().getName(),
                path,
                httpMethod,
                buildSummary(ex));
    }

    /** Kompakte Textrepraesentation fuer den Prompt: Typ, Meldung, gekuerzter Stack. */
    private static String buildSummary(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        sb.append(ex.getClass().getName());
        if (ex.getMessage() != null) {
            sb.append(": ").append(ex.getMessage());
        }
        StackTraceElement[] trace = ex.getStackTrace();
        int limit = Math.min(MAX_FRAMES, trace.length);
        for (int i = 0; i < limit; i++) {
            sb.append("\n\tat ").append(trace[i]);
        }
        Throwable cause = ex.getCause();
        if (cause != null && cause != ex) {
            sb.append("\nCaused by: ").append(cause.getClass().getName());
            if (cause.getMessage() != null) {
                sb.append(": ").append(cause.getMessage());
            }
        }
        return sb.toString();
    }
}
