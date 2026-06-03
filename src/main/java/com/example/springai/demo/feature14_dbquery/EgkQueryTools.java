package com.example.springai.demo.feature14_dbquery;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * FEATURE 14 – kuratierte Datenbank-Werkzeuge fuer den eGK-Datenbestand.
 *
 * <p>Statt das Modell selbst SQL erfinden zu lassen, bekommt es eine Handvoll
 * fester, parametrisierter Aggregations-Abfragen als Tools. Das ist der
 * <b>zuverlaessige</b> Pfad: Postgres erledigt das Zaehlen/Gruppieren ueber
 * Indizes auf Millionen Zeilen, das Modell waehlt nur Tool und Parameter und
 * formuliert das (kleine) Ergebnis aus. Jede Abfrage ist parametrisiert
 * (PreparedStatement) und damit injektionssicher.</p>
 */
@Component
public class EgkQueryTools {

    private final JdbcTemplate jdbc;

    public EgkQueryTools(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Tool(description = "Liefert die Gesamtzahl der Versicherten in der Datenbank.")
    public String gesamtzahlVersicherte() {
        return "Versicherte gesamt: " + count("SELECT COUNT(*) FROM versicherte");
    }

    @Tool(description = "Zaehlt, wie viele Diagnosen mit dem angegebenen ICD-10-Code erfasst sind, "
            + "und wie viele verschiedene Versicherte davon betroffen sind.")
    public String anzahlDiagnosenNachIcd(
            @ToolParam(description = "ICD-10-Code, z.B. 'E11.9' oder 'I10'") String icd10) {
        String code = icd10 == null ? "" : icd10.trim();
        Long diagnosen = jdbc.queryForObject(
                "SELECT COUNT(*) FROM diagnosen WHERE UPPER(icd10) = UPPER(?)", Long.class, code);
        Long betroffene = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT versicherte_id) FROM diagnosen WHERE UPPER(icd10) = UPPER(?)",
                Long.class, code);
        if (diagnosen == null || diagnosen == 0) {
            return "Keine Diagnosen mit ICD-10 '" + code + "' gefunden.";
        }
        return "ICD-10 '" + code + "': " + diagnosen + " Diagnosen bei " + betroffene + " Versicherten.";
    }

    @Tool(description = "Listet die haeufigsten Diagnosen (ICD-10) absteigend nach Anzahl auf.")
    public String haeufigsteDiagnosen(
            @ToolParam(description = "Wie viele Eintraege zurueckgeben (z.B. 5)") int limit) {
        int n = Math.max(1, Math.min(limit, 50));
        List<String> zeilen = jdbc.query(
                "SELECT icd10, MAX(diagnose_text) AS text, COUNT(*) AS anzahl "
                        + "FROM diagnosen GROUP BY icd10 ORDER BY anzahl DESC LIMIT " + n,
                (rs, i) -> rs.getString("icd10") + " (" + rs.getString("text") + "): "
                        + rs.getLong("anzahl"));
        return zeilen.isEmpty() ? "Keine Diagnosen vorhanden." : String.join("\n", zeilen);
    }

    @Tool(description = "Liefert die Verteilung der Zertifikats-Status (VALID/EXPIRED/REVOKED).")
    public String zertifikatsStatusVerteilung() {
        return verteilung("SELECT status, COUNT(*) FROM zertifikate GROUP BY status ORDER BY status",
                "Keine Zertifikate vorhanden.");
    }

    @Tool(description = "Liefert die Verteilung der eGK-Karten-Status (AKTIV/GESPERRT/ERSETZT).")
    public String egkKartenStatusVerteilung() {
        return verteilung("SELECT status, COUNT(*) FROM egk_karten GROUP BY status ORDER BY status",
                "Keine eGK-Karten vorhanden.");
    }

    @Tool(description = "Zaehlt die Versicherten je Krankenkasse (Name der Kasse und Anzahl).")
    public String versicherteProKrankenkasse() {
        List<String> zeilen = jdbc.query(
                "SELECT k.name AS name, COUNT(v.id) AS anzahl "
                        + "FROM krankenkassen k LEFT JOIN versicherte v ON v.krankenkasse_id = k.id "
                        + "GROUP BY k.name ORDER BY anzahl DESC",
                (rs, i) -> rs.getString("name") + ": " + rs.getLong("anzahl"));
        return zeilen.isEmpty() ? "Keine Krankenkassen vorhanden." : String.join("\n", zeilen);
    }

    // --- intern -------------------------------------------------------------

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0L : n;
    }

    /** Fuehrt eine zweispaltige (Schluessel, Anzahl)-Abfrage aus und formatiert sie. */
    private String verteilung(String sql, String leerText) {
        List<String> zeilen = jdbc.query(sql,
                (rs, i) -> rs.getString(1) + ": " + rs.getLong(2));
        return zeilen.isEmpty() ? leerText : zeilen.stream().collect(Collectors.joining(", "));
    }
}
