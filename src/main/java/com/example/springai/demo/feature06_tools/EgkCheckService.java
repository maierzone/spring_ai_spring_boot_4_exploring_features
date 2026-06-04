package com.example.springai.demo.feature06_tools;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.example.springai.demo.testdata.CheckDigits;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Fachlogik fuer eGK-/Telematik-<b>Einzelsatz-Pruefungen</b>.
 *
 * <p>Bewusste Abgrenzung zu Feature 14: dort beantwortet das Modell
 * <em>Aggregations</em>-Fragen ("wie viele", "Verteilung") ueber den gesamten
 * Datenbestand. Hier geht es um <em>einen konkreten Satz</em>: Ist diese Nummer
 * formal gueltig? Welchen Status hat die Karte dieses Versicherten? Was bedeutet
 * dieser ICD-10-Code? Ist dieses Zertifikat heute noch gueltig? Genau die Fragen,
 * die im Entwickleralltag beim Pruefen von Testdaten auftauchen.</p>
 *
 * <p>Die reinen Pruefziffer-Pruefungen nutzen die bereits getestete
 * {@link CheckDigits}-Logik (DRY); die Nachschlage-Funktionen treffen je genau
 * einen Datensatz ueber einen indizierten Schluessel (KVNR, Seriennummer).</p>
 */
@Service
public class EgkCheckService {

    private final JdbcTemplate jdbc;

    public EgkCheckService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // --- Reine Pruefziffer-Pruefungen (ohne DB) -----------------------------

    /** Ergebnis einer Format-/Pruefziffer-Pruefung. */
    public record CheckResult(boolean valid, String detail) {
    }

    /**
     * Prueft eine KVNR (1 Buchstabe + 8 Ziffern + 1 Pruefziffer = 10 Zeichen)
     * auf Format und korrekte GKV-Pruefziffer.
     */
    public CheckResult validateKvnr(String kvnr) {
        String s = kvnr == null ? "" : kvnr.trim().toUpperCase();
        if (s.length() != 10 || s.charAt(0) < 'A' || s.charAt(0) > 'Z'
                || !s.substring(1).chars().allMatch(Character::isDigit)) {
            return new CheckResult(false,
                    "Falsches Format: erwartet 1 Buchstabe + 9 Ziffern, z.B. 'A123456780'.");
        }
        char letter = s.charAt(0);
        String achtZiffern = s.substring(1, 9);
        int erwartet = CheckDigits.kvnrPruefziffer(letter, achtZiffern);
        int tatsaechlich = s.charAt(9) - '0';
        if (erwartet == tatsaechlich) {
            return new CheckResult(true, "Pruefziffer " + tatsaechlich + " korrekt.");
        }
        return new CheckResult(false,
                "Pruefziffer falsch: erwartet " + erwartet + ", angegeben " + tatsaechlich + ".");
    }

    /**
     * Prueft eine rein numerische Nummer (z.B. IK oder ICCSN) auf eine korrekte
     * Luhn-Pruefziffer (letzte Stelle gegen die uebrigen Stellen gerechnet).
     */
    public CheckResult validateLuhn(String nummer) {
        String s = nummer == null ? "" : nummer.trim();
        if (s.length() < 2 || !s.chars().allMatch(Character::isDigit)) {
            return new CheckResult(false, "Falsches Format: erwartet nur Ziffern (mind. 2 Stellen).");
        }
        String basis = s.substring(0, s.length() - 1);
        int erwartet = CheckDigits.luhnPruefziffer(basis);
        int tatsaechlich = s.charAt(s.length() - 1) - '0';
        if (erwartet == tatsaechlich) {
            return new CheckResult(true, "Luhn-Pruefziffer " + tatsaechlich + " korrekt.");
        }
        return new CheckResult(false,
                "Luhn-Pruefziffer falsch: erwartet " + erwartet + ", angegeben " + tatsaechlich + ".");
    }

    // --- Einzelsatz-Nachschlage (genau ein Datensatz) -----------------------

    /** Status und Gueltigkeitszeitraum der eGK eines Versicherten. */
    public record CardStatus(String kvnr, String name, String status,
                             LocalDate gueltigVon, LocalDate gueltigBis) {
    }

    /** Liefert die eGK-Karte zur angegebenen KVNR (genau ein Versicherter). */
    public Optional<CardStatus> cardStatusByKvnr(String kvnr) {
        String key = kvnr == null ? "" : kvnr.trim().toUpperCase();
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT v.kvnr, v.vorname, v.nachname, k.status, k.gueltig_von, k.gueltig_bis "
                            + "FROM versicherte v JOIN egk_karten k ON k.versicherte_id = v.id "
                            + "WHERE UPPER(v.kvnr) = ?",
                    (rs, i) -> new CardStatus(
                            rs.getString("kvnr"),
                            rs.getString("vorname") + " " + rs.getString("nachname"),
                            rs.getString("status"),
                            rs.getObject("gueltig_von", LocalDate.class),
                            rs.getObject("gueltig_bis", LocalDate.class)),
                    key));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Loest einen ICD-10-Code in seine im Datenbestand hinterlegten Klartexte auf
     * (Punktabfrage fuer genau einen Code, daher keine Aggregation).
     */
    public List<String> resolveIcd10(String icd10) {
        String code = icd10 == null ? "" : icd10.trim();
        return jdbc.queryForList(
                "SELECT DISTINCT diagnose_text FROM diagnosen WHERE UPPER(icd10) = UPPER(?) "
                        + "ORDER BY diagnose_text",
                String.class, code);
    }

    /** Zertifikat samt Gueltigkeit, bezogen auf einen Stichtag. */
    public record CertValidity(String seriennummer, String typ, String status,
                               LocalDate notBefore, LocalDate notAfter, boolean gueltigHeute) {
    }

    /** Liefert das Zertifikat zur angegebenen Seriennummer (genau ein Datensatz). */
    public Optional<CertValidity> certificateBySerial(String seriennummer) {
        String key = seriennummer == null ? "" : seriennummer.trim();
        try {
            return Optional.of(jdbc.queryForObject(
                    "SELECT seriennummer, typ, status, not_before, not_after "
                            + "FROM zertifikate WHERE seriennummer = ?",
                    (rs, i) -> {
                        LocalDate nb = rs.getObject("not_before", LocalDate.class);
                        LocalDate na = rs.getObject("not_after", LocalDate.class);
                        LocalDate heute = LocalDate.now();
                        boolean gueltig = "VALID".equals(rs.getString("status"))
                                && !heute.isBefore(nb) && !heute.isAfter(na);
                        return new CertValidity(rs.getString("seriennummer"), rs.getString("typ"),
                                rs.getString("status"), nb, na, gueltig);
                    },
                    key));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
