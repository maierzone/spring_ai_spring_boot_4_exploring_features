package com.example.springai.demo.feature21_exceptiontriage;

/**
 * Berechnet einen stabilen, menschenlesbaren Fingerabdruck einer Exception.
 *
 * <p>Idee (wie bei Sentry &amp; Co.): dieselbe Stoerung soll denselben Schluessel
 * ergeben, damit ein Fehler-<em>Sturm</em> (z.&nbsp;B. nach einem schlechten Deploy)
 * nur <em>einmal</em> analysiert wird und alle Wiederholungen darauf verweisen.
 * Der Fingerabdruck kombiniert den Exception-Typ mit dem ersten Stackframe aus
 * <em>eigenem</em> Code – die Zeile, die den Fehler tatsaechlich ausgeloest hat.</p>
 *
 * <p>Beispiel: {@code IllegalStateException@ExceptionTriageController.boom(line 42)}.
 * Der String ist zugleich Gruppierungs-Schluessel (Map/DB) und im UI direkt lesbar.</p>
 */
public final class ExceptionFingerprint {

    /** Nur Frames aus diesem Paket gelten als "eigener" Code. */
    private static final String OWN_PACKAGE = "com.example.springai";

    private ExceptionFingerprint() {
    }

    public static String of(Throwable ex) {
        String type = ex.getClass().getSimpleName();
        StackTraceElement frame = firstOwnFrame(ex);
        if (frame == null) {
            // Kein eigener Frame (z.B. reiner Framework-Stack) – auf obersten Frame ausweichen.
            StackTraceElement[] trace = ex.getStackTrace();
            frame = trace.length > 0 ? trace[0] : null;
        }
        if (frame == null) {
            return type;
        }
        return type + "@" + simpleClass(frame.getClassName()) + "." + frame.getMethodName()
                + "(line " + frame.getLineNumber() + ")";
    }

    private static StackTraceElement firstOwnFrame(Throwable ex) {
        for (StackTraceElement frame : ex.getStackTrace()) {
            if (frame.getClassName().startsWith(OWN_PACKAGE)) {
                return frame;
            }
        }
        return null;
    }

    private static String simpleClass(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
    }
}
