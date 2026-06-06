package com.example.springai.demo.feature06_tools;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.springai.demo.feature06_tools.EgkCheckService.CheckResult;

/**
 * FEATURE 6 – Vertiefung: <b>komplexe Parameter &amp; strukturierte Rueckgabe</b>.
 *
 * <p>Die anderen Tools in diesem Feature ({@link EgkCheckTools}, {@link DateTimeTools})
 * nehmen je einen {@code String} und geben einen fertig formulierten Satz zurueck.
 * Das ist der Einstieg. Spring AI kann aber deutlich mehr: Es leitet das JSON-Schema,
 * das dem Modell angeboten wird, automatisch aus den <em>Java-Typen</em> ab.</p>
 *
 * <p>Dieses Tool demonstriert beide Enden des Spektrums in einer Methode:</p>
 * <ul>
 *   <li><b>Komplexer Parameter:</b> Statt vier Einzelargumenten nimmt
 *       {@link #pruefeStammsatz} <em>ein</em> Objekt {@link Stammsatz}. Spring AI baut
 *       daraus ein verschachteltes JSON-Schema – inklusive {@link LocalDate} (als
 *       Datums-String) und einem {@code enum} ({@link Versichertenart}), das dem Modell
 *       als feste Auswahl erscheint. Das Modell muss also ein strukturiertes Objekt
 *       fuellen, nicht nur einen Wert raten.</li>
 *   <li><b>Strukturierte Rueckgabe:</b> Zurueck kommt kein Satz, sondern ein
 *       {@link PlausibilitaetsReport} mit typisierten Feldern und einer
 *       {@code List<Befund>}. Spring AI serialisiert das zu JSON; das Modell liest
 *       die Felder gezielt aus, statt einen Fliesstext zu interpretieren.</li>
 * </ul>
 *
 * <p>Fachlich bleibt es im eGK-Kontext: Es buendelt die schon vorhandenen
 * Einzelpruefungen aus {@link EgkCheckService} zu einer Stammsatz-Plausibilitaet.</p>
 */
@Component
public class StammdatenPlausibilitaetTool {

    private final EgkCheckService service;

    public StammdatenPlausibilitaetTool(EgkCheckService service) {
        this.service = service;
    }

    /** Versichertenart – als {@code enum} erscheint dies im Schema als feste Auswahl. */
    public enum Versichertenart {
        MITGLIED, FAMILIENVERSICHERT, RENTNER
    }

    /**
     * Eingabe-Objekt: ein kompletter Versicherten-Stammsatz. Als Record-Parameter
     * wird hieraus ein verschachteltes JSON-Schema – jede Komponente ist eine
     * Property, die das Modell befuellen muss.
     */
    public record Stammsatz(
            @ToolParam(description = "Krankenversichertennummer (KVNR), 1 Buchstabe + 9 Ziffern, z.B. 'A123456780'")
            String kvnr,

            @ToolParam(description = "Institutionskennzeichen (IK), reine Ziffernfolge mit Luhn-Pruefziffer")
            String institutionskennzeichen,

            @ToolParam(description = "Geburtsdatum im ISO-Format JJJJ-MM-TT")
            LocalDate geburtsdatum,

            @ToolParam(description = "Versichertenart: MITGLIED, FAMILIENVERSICHERT oder RENTNER")
            Versichertenart art) {
    }

    /** Einzelner Pruefbefund zu genau einem Feld des Stammsatzes. */
    public record Befund(String feld, boolean plausibel, String detail) {
    }

    /**
     * Strukturierter Gesamtbefund. Das Modell kann {@code insgesamtPlausibel} direkt
     * als Ja/Nein nutzen und bei Bedarf in {@code befunde} begruenden.
     */
    public record PlausibilitaetsReport(boolean insgesamtPlausibel, int anzahlBefunde, List<Befund> befunde) {
    }

    @Tool(description = "Prueft einen kompletten Versicherten-Stammsatz (KVNR, Institutionskennzeichen, "
            + "Geburtsdatum, Versichertenart) in einem Durchgang auf formale Plausibilitaet und liefert "
            + "einen strukturierten Befund-Report mit Einzelergebnis je Feld.")
    public PlausibilitaetsReport pruefeStammsatz(
            @ToolParam(description = "Der zu pruefende Versicherten-Stammsatz") Stammsatz satz) {
        List<Befund> befunde = new ArrayList<>();

        CheckResult kvnr = service.validateKvnr(satz.kvnr());
        befunde.add(new Befund("kvnr", kvnr.valid(), kvnr.detail()));

        CheckResult ik = service.validateLuhn(satz.institutionskennzeichen());
        befunde.add(new Befund("institutionskennzeichen", ik.valid(), ik.detail()));

        befunde.add(pruefeGeburtsdatum(satz.geburtsdatum(), satz.art()));

        boolean gesamt = befunde.stream().allMatch(Befund::plausibel);
        return new PlausibilitaetsReport(gesamt, befunde.size(), befunde);
    }

    /**
     * Plausibilisiert das Geburtsdatum: muss in der Vergangenheit liegen, ein Alter
     * von &gt; 120 Jahren gilt als unplausibel. Zusaetzlich ein leichter
     * Konsistenz-Check gegen die {@link Versichertenart} – so beeinflusst auch das
     * {@code enum}-Feld das Ergebnis.
     */
    private Befund pruefeGeburtsdatum(LocalDate geburtsdatum, Versichertenart art) {
        if (geburtsdatum == null) {
            return new Befund("geburtsdatum", false, "Kein Geburtsdatum angegeben.");
        }
        LocalDate heute = LocalDate.now();
        if (geburtsdatum.isAfter(heute)) {
            return new Befund("geburtsdatum", false, "Geburtsdatum liegt in der Zukunft.");
        }
        int alter = Period.between(geburtsdatum, heute).getYears();
        if (alter > 120) {
            return new Befund("geburtsdatum", false, "Unplausibles Alter: " + alter + " Jahre.");
        }
        if (art == Versichertenart.RENTNER && alter < 60) {
            return new Befund("geburtsdatum", false,
                    "Versichertenart RENTNER, aber Alter erst " + alter + " Jahre.");
        }
        return new Befund("geburtsdatum", true, "Alter " + alter + " Jahre, plausibel.");
    }
}
