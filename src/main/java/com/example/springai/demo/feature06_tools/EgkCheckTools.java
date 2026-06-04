package com.example.springai.demo.feature06_tools;

import com.example.springai.demo.feature06_tools.EgkCheckService.CardStatus;
import com.example.springai.demo.feature06_tools.EgkCheckService.CertValidity;
import com.example.springai.demo.feature06_tools.EgkCheckService.CheckResult;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Werkzeuge, die die eGK-Einzelsatz-Pruefungen ({@link EgkCheckService}) fuer das
 * Modell zugaenglich machen.
 *
 * <p>Das ist das <b>produktiv relevante Muster</b> beim Tool Calling: Ein Tool ist
 * eine duenne, gut dokumentierte Fassade vor vorhandener Geschaeftslogik. Weil
 * {@code EgkCheckTools} eine Spring-Bean ist, kann es per Dependency Injection auf
 * Services und Repositories zugreifen – das Modell ruft am Ende ganz normalen,
 * getesteten Anwendungscode auf. Die Tools formatieren nur das Ergebnis in einen
 * Satz, den das Modell weiterverwenden kann.</p>
 */
@Component
public class EgkCheckTools {

    private final EgkCheckService service;

    public EgkCheckTools(EgkCheckService service) {
        this.service = service;
    }

    @Tool(description = "Prueft, ob eine Krankenversichertennummer (KVNR) formal gueltig ist "
            + "(1 Buchstabe + 9 Ziffern, letzte Ziffer ist die GKV-Pruefziffer).")
    public String pruefeKvnr(
            @ToolParam(description = "Die KVNR, z.B. 'A123456780'") String kvnr) {
        CheckResult r = service.validateKvnr(kvnr);
        return "KVNR '" + kvnr + "': " + (r.valid() ? "GUELTIG" : "UNGUELTIG") + " – " + r.detail();
    }

    @Tool(description = "Prueft eine rein numerische Nummer wie ein Institutionskennzeichen (IK) "
            + "oder eine eGK-ICCSN auf eine korrekte Luhn-Pruefziffer.")
    public String pruefeLuhnNummer(
            @ToolParam(description = "Die Ziffernfolge inkl. Pruefziffer, z.B. eine IK oder ICCSN") String nummer) {
        CheckResult r = service.validateLuhn(nummer);
        return "Nummer '" + nummer + "': " + (r.valid() ? "GUELTIG" : "UNGUELTIG") + " – " + r.detail();
    }

    @Tool(description = "Liefert Status und Gueltigkeitszeitraum der elektronischen Gesundheitskarte (eGK) "
            + "eines Versicherten anhand seiner KVNR.")
    public String egkKartenStatus(
            @ToolParam(description = "Die KVNR des Versicherten, z.B. 'A123456780'") String kvnr) {
        return service.cardStatusByKvnr(kvnr)
                .map(c -> "eGK von " + c.name() + " (" + c.kvnr() + "): Status " + c.status()
                        + ", gueltig " + c.gueltigVon() + " bis " + c.gueltigBis() + ".")
                .orElse("Kein Versicherter mit eGK zur KVNR '" + kvnr + "' gefunden.");
    }

    @Tool(description = "Loest einen ICD-10-Diagnosecode in seinen Klartext (Diagnose-Bezeichnung) auf, "
            + "wie er im Datenbestand hinterlegt ist.")
    public String loeseIcd10Auf(
            @ToolParam(description = "Der ICD-10-Code, z.B. 'E11.9' oder 'I10'") String icd10) {
        List<String> texte = service.resolveIcd10(icd10);
        if (texte.isEmpty()) {
            return "Kein Diagnosetext zu ICD-10 '" + icd10 + "' im Datenbestand gefunden.";
        }
        return "ICD-10 '" + icd10 + "': " + String.join("; ", texte);
    }

    @Tool(description = "Prueft, ob ein digitales Zertifikat (HBA/SMC_B/EGK_AUT/EGK_ENC) anhand seiner "
            + "Seriennummer heute gueltig ist (Status VALID und Stichtag im Gueltigkeitszeitraum).")
    public String pruefeZertifikat(
            @ToolParam(description = "Die hexadezimale Seriennummer des Zertifikats") String seriennummer) {
        return service.certificateBySerial(seriennummer)
                .map(c -> "Zertifikat " + c.seriennummer() + " (" + c.typ() + "): "
                        + (c.gueltigHeute() ? "GUELTIG" : "NICHT GUELTIG")
                        + " – Status " + c.status() + ", " + c.notBefore() + " bis " + c.notAfter() + ".")
                .orElse("Kein Zertifikat mit Seriennummer '" + seriennummer + "' gefunden.");
    }
}
