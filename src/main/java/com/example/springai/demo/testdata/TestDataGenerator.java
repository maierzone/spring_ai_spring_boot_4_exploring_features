package com.example.springai.demo.testdata;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

@Component
public class TestDataGenerator {

    /** Fester Standard-Seed: gleicher Seed + gleiche Anzahl =&gt; exakt gleiche Daten. */
    public static final long DEFAULT_SEED = 42L;

    /**
     * Fester Bezugszeitpunkt fuer Gueltigkeiten/Status. Bewusst fix (nicht
     * {@code now()}), damit die Daten – und damit die Tests – unabhaengig vom
     * Ausfuehrungsdatum reproduzierbar bleiben.
     */
    public static final LocalDate STICHTAG = LocalDate.of(2026, 1, 1);

    // --- Bausteinlisten (synthetisch) ---------------------------------------
    private static final String[] VORNAMEN_M = {
            "Lukas", "Jonas", "Felix", "Paul", "Leon", "Maximilian", "Elias", "Noah", "Ben", "Finn"};
    private static final String[] VORNAMEN_W = {
            "Anna", "Marie", "Sophie", "Emma", "Mia", "Lena", "Laura", "Hannah", "Lea", "Clara"};
    private static final String[] NACHNAMEN = {
            "Mueller", "Schmidt", "Schneider", "Fischer", "Weber", "Meyer", "Wagner", "Becker",
            "Hoffmann", "Schulz", "Koch", "Bauer", "Richter", "Klein", "Wolf", "Schroeder"};
    private static final String[] STRASSEN = {
            "Hauptstrasse", "Schulstrasse", "Gartenstrasse", "Bahnhofstrasse", "Dorfstrasse",
            "Lindenweg", "Bergstrasse", "Birkenweg", "Goethestrasse", "Schillerstrasse"};

    /** PLZ/Ort-Paare (frei gewaehlte, reale Orte – ohne Personenbezug). */
    private static final String[][] ORTE = {
            {"80331", "Muenchen"}, {"10115", "Berlin"}, {"20095", "Hamburg"}, {"50667", "Koeln"},
            {"60311", "Frankfurt am Main"}, {"70173", "Stuttgart"}, {"04109", "Leipzig"},
            {"01067", "Dresden"}, {"30159", "Hannover"}, {"90402", "Nuernberg"}};

    /** Krankenkassen-Namen (Typ): synthetische IK wird im Generator ergaenzt. */
    private static final String[][] KRANKENKASSEN = {
            {"AOK Bayern", "GKV"}, {"Techniker Krankenkasse", "GKV"}, {"BARMER", "GKV"},
            {"DAK-Gesundheit", "GKV"}, {"IKK classic", "GKV"}, {"KKH Kaufmaennische", "GKV"},
            {"Knappschaft", "GKV"}, {"AOK Nordost", "GKV"}, {"BKK VBU", "GKV"},
            {"mhplus BKK", "GKV"}, {"Debeka", "PKV"}, {"Allianz Private KV", "PKV"}};

    /** ICD-10-Code + Klartext (haeufige Diagnosen). */
    private static final String[][] ICD10 = {
            {"E11.9", "Diabetes mellitus, Typ 2, ohne Komplikationen"},
            {"I10", "Essentielle (primaere) Hypertonie"},
            {"J06.9", "Akute Infektion der oberen Atemwege, nicht naeher bezeichnet"},
            {"M54.5", "Kreuzschmerz"},
            {"F32.1", "Mittelgradige depressive Episode"},
            {"K21.0", "Gastrooesophageale Refluxkrankheit mit Oesophagitis"},
            {"J45.9", "Asthma bronchiale, nicht naeher bezeichnet"},
            {"E78.5", "Hyperlipidaemie, nicht naeher bezeichnet"},
            {"N39.0", "Harnwegsinfektion, Lokalisation nicht naeher bezeichnet"},
            {"M17.9", "Gonarthrose, nicht naeher bezeichnet"},
            {"G43.9", "Migraene, nicht naeher bezeichnet"},
            {"L20.9", "Atopisches (endogenes) Ekzem, nicht naeher bezeichnet"},
            {"R51", "Kopfschmerz"},
            {"B34.9", "Virusinfektion, nicht naeher bezeichnet"},
            {"Z00.0", "Aerztliche Allgemeinuntersuchung"}};

    /** Ausstellende CAs (gematik-naher Aufbau, als TEST-ONLY gekennzeichnet). */
    private static final String[] CA_NAMEN = {
            "GEM.RCA3 TEST-ONLY", "GEM.HBA-CA24 TEST-ONLY",
            "GEM.SMCB-CA9 TEST-ONLY", "GEM.EGK-CA10 TEST-ONLY"};

    /**
     * Erzeugt einen Datensatz mit Standard-Seed.
     *
     * @param versichertenAnzahl Anzahl Versicherte (steuert das Gesamtvolumen)
     */
    public Dataset generate(int versichertenAnzahl) {
        return generate(versichertenAnzahl, DEFAULT_SEED);
    }

    /**
     * Erzeugt einen vollstaendigen, in sich konsistenten Datensatz.
     *
     * @param versichertenAnzahl Anzahl Versicherte (&gt; 0)
     * @param seed               Zufalls-Seed (gleicher Seed =&gt; gleiche Daten)
     */
    public Dataset generate(int versichertenAnzahl, long seed) {
        if (versichertenAnzahl <= 0) {
            throw new IllegalArgumentException("versichertenAnzahl muss > 0 sein: " + versichertenAnzahl);
        }
        Random r = new Random(seed);

        List<Krankenkasse> kassen = krankenkassen(r);
        List<CaZertifikat> cas = cas(r);
        int leAnzahl = Math.max(5, versichertenAnzahl / 50);
        List<Leistungserbringer> leistungserbringer = leistungserbringer(r, leAnzahl);
        List<Versicherter> versicherte = versicherte(r, versichertenAnzahl, kassen.size());
        List<EgkKarte> karten = karten(r, versicherte);
        List<Diagnose> diagnosen = diagnosen(r, versicherte, leistungserbringer.size());
        List<Zertifikat> zertifikate = zertifikate(r, leistungserbringer, karten, cas.size());

        return new Dataset(kassen, cas, leistungserbringer, versicherte, karten, diagnosen, zertifikate);
    }

    // ------------------------------------------------------------------------
    // Einzelne Tabellen
    // ------------------------------------------------------------------------

    private List<Krankenkasse> krankenkassen(Random r) {
        List<Krankenkasse> list = new ArrayList<>();
        for (int i = 0; i < KRANKENKASSEN.length; i++) {
            // IK: Klassifikation (z.B. 10) + 6 Ziffern + Luhn-Pruefziffer.
            String basis = "10" + ziffern(r, 6);
            String ik = CheckDigits.mitLuhn(basis);
            list.add(new Krankenkasse(i + 1, ik, KRANKENKASSEN[i][0], KRANKENKASSEN[i][1]));
        }
        return list;
    }

    private List<CaZertifikat> cas(Random r) {
        List<CaZertifikat> list = new ArrayList<>();
        for (int i = 0; i < CA_NAMEN.length; i++) {
            String name = CA_NAMEN[i];
            String dn = "CN=" + name + ",OU=Komponenten-CA der Telematikinfrastruktur,"
                    + "O=gematik GmbH NOT-VALID,C=DE";
            list.add(new CaZertifikat(i + 1, name, dn, hex(r, 16),
                    STICHTAG.minusYears(3), STICHTAG.plusYears(5)));
        }
        return list;
    }

    private List<Leistungserbringer> leistungserbringer(Random r, int anzahl) {
        List<Leistungserbringer> list = new ArrayList<>();
        for (int i = 0; i < anzahl; i++) {
            String[] ort = pick(r, ORTE);
            int w = r.nextInt(100);
            String typ = w < 70 ? "ARZT" : (w < 90 ? "APOTHEKE" : "KRANKENHAUS");
            String name;
            String lanr = null;
            String bsnr = ziffern(r, 9);
            if (typ.equals("ARZT")) {
                // LANR: 7-stellige Arztnummer (6 Ziffern + Luhn) + 2-stellige Fachgruppe.
                lanr = CheckDigits.mitLuhn(ziffern(r, 6)) + ziffern(r, 2);
                name = "Praxis Dr. " + pick(r, NACHNAMEN);
            } else if (typ.equals("APOTHEKE")) {
                name = pick(r, NACHNAMEN) + "-Apotheke";
            } else {
                name = "Klinikum " + ort[1];
            }
            list.add(new Leistungserbringer(i + 1, typ, name, lanr, bsnr, ort[0], ort[1]));
        }
        return list;
    }

    private List<Versicherter> versicherte(Random r, int anzahl, int kassenAnzahl) {
        List<Versicherter> list = new ArrayList<>();
        for (int i = 0; i < anzahl; i++) {
            boolean maennlich = r.nextBoolean();
            String geschlecht = maennlich ? "M" : "W";
            String vorname = maennlich ? pick(r, VORNAMEN_M) : pick(r, VORNAMEN_W);
            String nachname = pick(r, NACHNAMEN);
            char letter = (char) ('A' + r.nextInt(26));
            String kvnr = CheckDigits.kvnr(letter, ziffern(r, 8));
            LocalDate geburt = STICHTAG.minusDays(365L * (1 + r.nextInt(95)) + r.nextInt(365));
            String[] ort = pick(r, ORTE);
            String strasse = pick(r, STRASSEN) + " " + (1 + r.nextInt(199));
            long kasseId = 1 + r.nextInt(kassenAnzahl);
            list.add(new Versicherter(i + 1, kvnr, vorname, nachname, geburt, geschlecht,
                    ort[0], ort[1], strasse, kasseId));
        }
        return list;
    }

    private List<EgkKarte> karten(Random r, List<Versicherter> versicherte) {
        List<EgkKarte> list = new ArrayList<>();
        long id = 1;
        for (Versicherter v : versicherte) {
            // ICCSN deutscher eGK beginnt mit 80276 (D, Krankenversicherung).
            String iccsn = CheckDigits.mitLuhn("8027688" + ziffern(r, 12));
            String can = ziffern(r, 6);
            int w = r.nextInt(100);
            String status = w < 88 ? "AKTIV" : (w < 95 ? "GESPERRT" : "ERSETZT");
            LocalDate von = STICHTAG.minusYears(1 + r.nextInt(4));
            LocalDate bis = von.plusYears(6);
            list.add(new EgkKarte(id++, iccsn, can, v.id(), von, bis, status));
        }
        return list;
    }

    private List<Diagnose> diagnosen(Random r, List<Versicherter> versicherte, int leAnzahl) {
        List<Diagnose> list = new ArrayList<>();
        long id = 1;
        for (Versicherter v : versicherte) {
            int anzahl = 1 + r.nextInt(4); // 1..4 Diagnosen je Versichertem
            for (int d = 0; d < anzahl; d++) {
                String[] icd = pick(r, ICD10);
                long leId = 1 + r.nextInt(leAnzahl);
                LocalDate datum = STICHTAG.minusDays(r.nextInt(900));
                list.add(new Diagnose(id++, v.id(), leId, icd[0], icd[1], datum));
            }
        }
        return list;
    }

    private List<Zertifikat> zertifikate(Random r, List<Leistungserbringer> le,
                                         List<EgkKarte> karten, int caAnzahl) {
        List<Zertifikat> list = new ArrayList<>();
        long id = 1;
        // Je Leistungserbringer ein Profil-Zertifikat: Arzt -> HBA, sonst SMC-B.
        for (Leistungserbringer l : le) {
            String typ = l.typ().equals("ARZT") ? "HBA" : "SMC_B";
            String dn = "CN=" + l.name() + " " + typ + " TEST-ONLY,O=" + l.ort() + ",C=DE";
            list.add(zertifikat(r, id++, typ, dn, caAnzahl, "LEISTUNGSERBRINGER", l.id()));
        }
        // Je eGK-Karte zwei Zertifikate: Authentisierung (AUT) und Verschluesselung (ENC).
        for (EgkKarte k : karten) {
            String dn = "CN=Versicherter " + k.versicherteId() + " TEST-ONLY,O=eGK,C=DE";
            list.add(zertifikat(r, id++, "EGK_AUT", dn, caAnzahl, "VERSICHERTER", k.versicherteId()));
            list.add(zertifikat(r, id++, "EGK_ENC", dn, caAnzahl, "VERSICHERTER", k.versicherteId()));
        }
        return list;
    }

    /** Baut ein einzelnes Zertifikat mit konsistentem Status/Datums-Trio. */
    private Zertifikat zertifikat(Random r, long id, String typ, String dn, int caAnzahl,
                                  String inhaberTyp, long inhaberId) {
        long caId = 1 + r.nextInt(caAnzahl);
        String seriennummer = hex(r, 16);
        LocalDate notBefore = STICHTAG.minusYears(1 + r.nextInt(3));
        int w = r.nextInt(100);
        String status;
        LocalDate notAfter;
        LocalDate revocation = null;
        if (w < 85) {                       // gueltig: laeuft erst nach dem Stichtag ab
            status = "VALID";
            notAfter = STICHTAG.plusYears(1 + r.nextInt(4));
        } else if (w < 93) {                // abgelaufen: notAfter liegt vor dem Stichtag
            status = "EXPIRED";
            notAfter = STICHTAG.minusDays(1 + r.nextInt(700));
        } else {                            // gesperrt: revoziert vor dem Stichtag
            status = "REVOKED";
            notAfter = STICHTAG.plusYears(1 + r.nextInt(3));
            revocation = STICHTAG.minusDays(1 + r.nextInt(500));
        }
        return new Zertifikat(id, typ, seriennummer, dn, caId, inhaberTyp, inhaberId,
                notBefore, notAfter, status, revocation);
    }

    // ------------------------------------------------------------------------
    // Hilfsfunktionen
    // ------------------------------------------------------------------------

    private static <T> T pick(Random r, T[] array) {
        return array[r.nextInt(array.length)];
    }

    /** Zufaellige Ziffernfolge fester Laenge (kann fuehrende Nullen enthalten). */
    private static String ziffern(Random r, int laenge) {
        StringBuilder sb = new StringBuilder(laenge);
        for (int i = 0; i < laenge; i++) {
            sb.append(r.nextInt(10));
        }
        return sb.toString();
    }

    /** Zufaellige Hex-Zeichenkette fester Laenge (fuer Seriennummern). */
    private static String hex(Random r, int laenge) {
        StringBuilder sb = new StringBuilder(laenge);
        for (int i = 0; i < laenge; i++) {
            sb.append(Integer.toHexString(r.nextInt(16)));
        }
        return sb.toString().toUpperCase();
    }

    // ------------------------------------------------------------------------
    // Records (reine Datentraeger, IDs werden deterministisch vergeben)
    // ------------------------------------------------------------------------

    public record Krankenkasse(long id, String ik, String name, String typ) {
    }

    public record CaZertifikat(long id, String name, String subjectDn, String seriennummer,
                               LocalDate notBefore, LocalDate notAfter) {
    }

    public record Leistungserbringer(long id, String typ, String name, String lanr, String bsnr,
                                     String plz, String ort) {
    }

    public record Versicherter(long id, String kvnr, String vorname, String nachname,
                               LocalDate geburtsdatum, String geschlecht, String plz, String ort,
                               String strasse, long krankenkasseId) {
    }

    public record EgkKarte(long id, String iccsn, String can, long versicherteId,
                           LocalDate gueltigVon, LocalDate gueltigBis, String status) {
    }

    public record Diagnose(long id, long versicherteId, long leistungserbringerId, String icd10,
                           String diagnoseText, LocalDate datum) {
    }

    public record Zertifikat(long id, String typ, String seriennummer, String subjectDn, long caId,
                             String inhaberTyp, long inhaberId, LocalDate notBefore,
                             LocalDate notAfter, String status, LocalDate revocationDatum) {
    }

    /** Vollstaendiger, in sich konsistenter Datensatz. */
    public record Dataset(List<Krankenkasse> krankenkassen, List<CaZertifikat> caZertifikate,
                          List<Leistungserbringer> leistungserbringer, List<Versicherter> versicherte,
                          List<EgkKarte> karten, List<Diagnose> diagnosen,
                          List<Zertifikat> zertifikate) {
    }
}
