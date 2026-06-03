package com.example.springai.demo.feature14_dbquery;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.springai.demo.testdata.EgkTestDataRepository;
import com.example.springai.demo.testdata.TestDataGenerator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * Testet die kuratierten Abfrage-Tools gegen eine H2-DB mit deterministisch
 * generierten Testdaten – ganz ohne Modell. Die Erwartungen ergeben sich aus dem
 * festen Seed des {@link TestDataGenerator}.
 */
class EgkQueryToolsTest {

    private EmbeddedDatabase db;
    private EgkQueryTools tools;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema-egk.sql")
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        new EgkTestDataRepository(jdbc).replaceAll(new TestDataGenerator().generate(100, 42L));
        tools = new EgkQueryTools(jdbc);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void zaehltVersicherte() {
        assertThat(tools.gesamtzahlVersicherte()).contains("100");
    }

    @Test
    void zaehltDiagnoseNachIcdCaseInsensitiv() {
        // Gross-/Kleinschreibung des Codes darf die gezaehlten Mengen nicht aendern
        // (der Code selbst wird in Originalschreibweise zurueckgespiegelt).
        String gross = tools.anzahlDiagnosenNachIcd("E11.9");
        String klein = tools.anzahlDiagnosenNachIcd("e11.9");
        String mengen = gross.substring(gross.indexOf(':'));
        assertThat(klein).endsWith(mengen);
        assertThat(gross).contains("Diagnosen bei").contains("Versicherten");
    }

    @Test
    void meldetUnbekanntenIcdCode() {
        assertThat(tools.anzahlDiagnosenNachIcd("X99.9")).contains("Keine Diagnosen");
    }

    @Test
    void listetHaeufigsteDiagnosenBegrenzt() {
        String out = tools.haeufigsteDiagnosen(3);
        // Genau drei Zeilen (durch das LIMIT begrenzt).
        assertThat(out.lines().count()).isEqualTo(3);
    }

    @Test
    void zertifikatsStatusDeckenAlleZertifikateAb() {
        String out = tools.zertifikatsStatusVerteilung();
        assertThat(out).containsAnyOf("VALID", "EXPIRED", "REVOKED");
    }

    @Test
    void summeProKrankenkasseErgibtAlleVersicherten() {
        long summe = tools.versicherteProKrankenkasse().lines()
                .mapToLong(z -> Long.parseLong(z.substring(z.lastIndexOf(": ") + 2)))
                .sum();
        assertThat(summe).isEqualTo(100);
    }

    @Test
    void summeGeschlechtErgibtAlleVersicherten() {
        assertThat(summeVerteilung(tools.versicherteNachGeschlecht())).isEqualTo(100);
    }

    @Test
    void leistungserbringerTypenSummierenSichZurGesamtzahl() {
        long gesamt = Long.parseLong(
                tools.gesamtzahlLeistungserbringer().replaceAll("\\D+", ""));
        assertThat(gesamt).isPositive()
                .isEqualTo(summeVerteilung(tools.leistungserbringerNachTyp()));
    }

    @Test
    void topOrteVersicherteRespektiertLimit() {
        assertThat(tools.topOrteVersicherte(3).lines().count()).isEqualTo(3);
    }

    @Test
    void krankenkassenNachTypNenntGkvOderPkv() {
        assertThat(tools.krankenkassenNachTyp()).containsAnyOf("GKV", "PKV");
    }

    @Test
    void gueltigeCaZertifikateMeldetTeilmenge() {
        long gesamt = Long.parseLong(
                tools.gesamtzahlCaZertifikate().replaceAll("\\D+", ""));
        String out = tools.gueltigeCaZertifikate();
        // Format: "CA-Zertifikate: <gueltig> von <gesamt> aktuell gueltig."
        long genannteGesamt = Long.parseLong(out.replaceAll(".*von (\\d+).*", "$1"));
        assertThat(gesamt).isPositive().isEqualTo(genannteGesamt);
    }

    @Test
    void zertifikateTypenSummierenSichZurGesamtzahl() {
        long gesamt = Long.parseLong(
                tools.gesamtzahlZertifikate().replaceAll("\\D+", ""));
        assertThat(gesamt).isPositive()
                .isEqualTo(summeVerteilung(tools.zertifikateNachTyp()));
    }

    /** Summiert die Zaehlwerte einer komma-getrennten Verteilung "k1: n1, k2: n2". */
    private static long summeVerteilung(String verteilung) {
        return java.util.Arrays.stream(verteilung.split(", "))
                .mapToLong(p -> Long.parseLong(p.substring(p.lastIndexOf(": ") + 2)))
                .sum();
    }
}
