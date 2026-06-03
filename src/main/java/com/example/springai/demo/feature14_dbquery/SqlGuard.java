package com.example.springai.demo.feature14_dbquery;

import java.util.regex.Pattern;

/**
 * Reine, zustandslose Schutzlogik fuer den Text-to-SQL-Fallback.
 *
 * <p>Das Modell darf bei diesem Feature im Notfall selbst SQL erzeugen. Damit
 * daraus kein Schaden entsteht, laesst {@link #sanitize(String)} nur lesende
 * Einzel-Abfragen durch: genau ein {@code SELECT}- bzw. {@code WITH}-Statement,
 * keine datenveraendernden Schluesselwoerter, keine Kommentare, kein zweites
 * Statement. Fehlt ein {@code LIMIT}, wird eines ergaenzt, damit eine versehentlich
 * unbeschraenkte Abfrage nicht Millionen Zeilen zieht.</p>
 *
 * <p>Bewusst frei von Spring-/DB-Abhaengigkeiten, daher voll offline testbar.</p>
 */
public final class SqlGuard {

    /** Obergrenze, die ergaenzt wird, wenn die Abfrage selbst kein LIMIT nennt. */
    public static final int DEFAULT_LIMIT = 100;

    /** Datenveraendernde / gefaehrliche Schluesselwoerter (als ganze Woerter). */
    private static final Pattern VERBOTEN = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|truncate|create|grant|revoke|merge|"
                    + "call|exec|execute|copy|attach|vacuum|pg_sleep|pg_read_file)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Erlaubter Start einer reinen Lese-Abfrage. */
    private static final Pattern START_SELECT = Pattern.compile(
            "^(select|with)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern HAT_LIMIT = Pattern.compile(
            "\\blimit\\b", Pattern.CASE_INSENSITIVE);

    private SqlGuard() {
    }

    /**
     * Prueft die Abfrage und liefert eine ausfuehrbare, abgesicherte Variante.
     *
     * @param sql vom Modell vorgeschlagenes SQL
     * @return das (ggf. um ein LIMIT ergaenzte) SELECT
     * @throws IllegalArgumentException wenn die Abfrage nicht rein lesend ist
     */
    public static String sanitize(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("Leere SQL-Abfrage.");
        }
        // Ein einzelnes abschliessendes Semikolon ist erlaubt und wird entfernt.
        String s = sql.strip();
        if (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).strip();
        }
        if (s.contains(";")) {
            throw new IllegalArgumentException("Nur ein einzelnes Statement erlaubt.");
        }
        if (s.contains("--") || s.contains("/*")) {
            throw new IllegalArgumentException("Kommentare sind nicht erlaubt.");
        }
        if (!START_SELECT.matcher(s).find()) {
            throw new IllegalArgumentException("Nur SELECT-/WITH-Abfragen erlaubt.");
        }
        if (VERBOTEN.matcher(s).find()) {
            throw new IllegalArgumentException("Datenveraendernde Schluesselwoerter sind verboten.");
        }
        if (!HAT_LIMIT.matcher(s).find()) {
            s = s + " LIMIT " + DEFAULT_LIMIT;
        }
        return s;
    }
}
