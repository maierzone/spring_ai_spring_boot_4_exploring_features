package com.example.springai.demo.feature14_dbquery;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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

    @Tool(description = "Liefert die Gesamtzahl der Leistungserbringer (Aerzte/Apotheken/Krankenhaeuser).")
    public String gesamtzahlLeistungserbringer() {
        return "Leistungserbringer gesamt: " + count("SELECT COUNT(*) FROM leistungserbringer");
    }

    @Tool(description = "Liefert die Verteilung der Leistungserbringer nach Typ (ARZT/APOTHEKE/KRANKENHAUS).")
    public String leistungserbringerNachTyp() {
        return verteilung(
                "SELECT typ, COUNT(*) FROM leistungserbringer GROUP BY typ ORDER BY typ",
                "Keine Leistungserbringer vorhanden.");
    }

    @Tool(description = "Listet die Orte mit den meisten Leistungserbringern absteigend auf.")
    public String topOrteLeistungserbringer(
            @ToolParam(description = "Wie viele Orte zurueckgeben (z.B. 5)") int limit) {
        return topOrte("leistungserbringer", limit);
    }

    @Tool(description = "Liefert die Verteilung der Versicherten nach Geschlecht (M/W/D).")
    public String versicherteNachGeschlecht() {
        return verteilung(
                "SELECT geschlecht, COUNT(*) FROM versicherte GROUP BY geschlecht ORDER BY geschlecht",
                "Keine Versicherten vorhanden.");
    }

    @Tool(description = "Listet die Orte mit den meisten Versicherten absteigend auf.")
    public String topOrteVersicherte(
            @ToolParam(description = "Wie viele Orte zurueckgeben (z.B. 5)") int limit) {
        return topOrte("versicherte", limit);
    }

    @Tool(description = "Liefert die Gesamtzahl der erfassten Diagnosen.")
    public String gesamtzahlDiagnosen() {
        return "Diagnosen gesamt: " + count("SELECT COUNT(*) FROM diagnosen");
    }

    @Tool(description = "Liefert die Gesamtzahl der eGK-Karten.")
    public String gesamtzahlEgkKarten() {
        return "eGK-Karten gesamt: " + count("SELECT COUNT(*) FROM egk_karten");
    }

    @Tool(description = "Liefert die Gesamtzahl der digitalen Zertifikate (HBA/SMC_B/EGK_AUT/EGK_ENC).")
    public String gesamtzahlZertifikate() {
        return "Zertifikate gesamt: " + count("SELECT COUNT(*) FROM zertifikate");
    }

    @Tool(description = "Liefert die Verteilung der Zertifikate nach Typ (HBA/SMC_B/EGK_AUT/EGK_ENC).")
    public String zertifikateNachTyp() {
        return verteilung(
                "SELECT typ, COUNT(*) FROM zertifikate GROUP BY typ ORDER BY typ",
                "Keine Zertifikate vorhanden.");
    }

    @Tool(description = "Liefert die Gesamtzahl der Krankenkassen.")
    public String gesamtzahlKrankenkassen() {
        return "Krankenkassen gesamt: " + count("SELECT COUNT(*) FROM krankenkassen");
    }

    @Tool(description = "Liefert die Verteilung der Krankenkassen nach Typ (GKV/PKV).")
    public String krankenkassenNachTyp() {
        return verteilung(
                "SELECT typ, COUNT(*) FROM krankenkassen GROUP BY typ ORDER BY typ",
                "Keine Krankenkassen vorhanden.");
    }

    @Tool(description = "Liefert die Gesamtzahl der ausstellenden CA-Zertifikate (Trust-Chain).")
    public String gesamtzahlCaZertifikate() {
        return "CA-Zertifikate gesamt: " + count("SELECT COUNT(*) FROM ca_zertifikate");
    }

    @Tool(description = "Zaehlt die aktuell gueltigen CA-Zertifikate "
            + "(Stichtag heute zwischen not_before und not_after).")
    public String gueltigeCaZertifikate() {
        long gesamt = count("SELECT COUNT(*) FROM ca_zertifikate");
        long gueltig = count("SELECT COUNT(*) FROM ca_zertifikate "
                + "WHERE not_before <= CURRENT_DATE AND not_after >= CURRENT_DATE");
        return "CA-Zertifikate: " + gueltig + " von " + gesamt + " aktuell gueltig.";
    }

    // --- intern -------------------------------------------------------------

    /**
     * Top-N Orte einer Tabelle mit Spalte {@code ort}. Der Tabellenname stammt
     * ausschliesslich aus festen internen String-Literalen (nie aus Modell-/
     * Nutzereingaben), daher ist die Konkatenation hier unkritisch.
     */
    private String topOrte(String tabelle, int limit) {
        int n = Math.max(1, Math.min(limit, 50));
        List<String> zeilen = jdbc.query(
                "SELECT ort, COUNT(*) AS anzahl FROM " + tabelle
                        + " GROUP BY ort ORDER BY anzahl DESC LIMIT " + n,
                (rs, i) -> rs.getString("ort") + ": " + rs.getLong("anzahl"));
        return zeilen.isEmpty() ? "Keine Daten vorhanden." : String.join("\n", zeilen);
    }

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
