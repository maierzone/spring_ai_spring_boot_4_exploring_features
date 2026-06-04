package com.example.springai.demo.feature06_tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.springai.demo.feature06_tools.EgkCheckService.CardStatus;
import com.example.springai.demo.feature06_tools.EgkCheckService.CertValidity;
import com.example.springai.demo.testdata.CheckDigits;
import com.example.springai.demo.testdata.EgkTestDataRepository;
import com.example.springai.demo.testdata.TestDataGenerator;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * Testet die eGK-Einzelsatz-Pruefungen ohne Modell: die reinen Pruefziffer-Checks
 * deterministisch, die Nachschlage-Funktionen gegen eine H2-DB mit generierten
 * Testdaten. Erwartungswerte werden aus der DB selbst gezogen, damit der Test
 * nicht an konkreten Seed-Werten klebt.
 */
class EgkCheckServiceTest {

    private EmbeddedDatabase db;
    private JdbcTemplate jdbc;
    private EgkCheckService service;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema-egk.sql")
                .build();
        jdbc = new JdbcTemplate(db);
        new EgkTestDataRepository(jdbc).replaceAll(new TestDataGenerator().generate(100, 42L));
        service = new EgkCheckService(jdbc);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void erkenntKorrekteKvnr() {
        // Eine KVNR mit korrekt berechneter Pruefziffer muss akzeptiert werden.
        String kvnr = CheckDigits.kvnr('A', "12345678");
        assertThat(service.validateKvnr(kvnr).valid()).isTrue();
    }

    @Test
    void erkenntFalschePruefziffer() {
        String gueltig = CheckDigits.kvnr('A', "12345678");
        char letzte = gueltig.charAt(9);
        char andere = letzte == '0' ? '1' : '0';
        String verfaelscht = gueltig.substring(0, 9) + andere;
        assertThat(service.validateKvnr(verfaelscht).valid()).isFalse();
    }

    @Test
    void lehntFalschesKvnrFormatAb() {
        assertThat(service.validateKvnr("12345").valid()).isFalse();
        assertThat(service.validateKvnr("1234567890").valid()).isFalse(); // kein Buchstabe vorn
    }

    @Test
    void erkenntKorrekteLuhnNummer() {
        String mitPruefziffer = CheckDigits.mitLuhn("12345678");
        assertThat(service.validateLuhn(mitPruefziffer).valid()).isTrue();
    }

    @Test
    void erkenntFalscheLuhnNummer() {
        String gueltig = CheckDigits.mitLuhn("12345678");
        char letzte = gueltig.charAt(gueltig.length() - 1);
        char andere = letzte == '0' ? '1' : '0';
        String verfaelscht = gueltig.substring(0, gueltig.length() - 1) + andere;
        assertThat(service.validateLuhn(verfaelscht).valid()).isFalse();
    }

    @Test
    void findetKartenstatusZuExistierenderKvnr() {
        String kvnr = jdbc.queryForObject(
                "SELECT v.kvnr FROM versicherte v JOIN egk_karten k ON k.versicherte_id = v.id LIMIT 1",
                String.class);
        Optional<CardStatus> status = service.cardStatusByKvnr(kvnr);
        assertThat(status).isPresent();
        assertThat(status.get().status()).isIn("AKTIV", "GESPERRT", "ERSETZT");
        assertThat(status.get().name()).isNotBlank();
    }

    @Test
    void meldetUnbekannteKvnr() {
        assertThat(service.cardStatusByKvnr("Z999999990")).isEmpty();
    }

    @Test
    void loestExistierendenIcd10CodeAuf() {
        String icd = jdbc.queryForObject("SELECT icd10 FROM diagnosen LIMIT 1", String.class);
        assertThat(service.resolveIcd10(icd)).isNotEmpty();
    }

    @Test
    void meldetUnbekanntenIcd10Code() {
        assertThat(service.resolveIcd10("X99.9")).isEmpty();
    }

    @Test
    void prueftZertifikatGueltigkeitKonsistentZumStatus() {
        String serial = jdbc.queryForObject("SELECT seriennummer FROM zertifikate LIMIT 1", String.class);
        Optional<CertValidity> cert = service.certificateBySerial(serial);
        assertThat(cert).isPresent();
        // Nur VALID-Zertifikate koennen am Stichtag gueltig sein.
        if (!"VALID".equals(cert.get().status())) {
            assertThat(cert.get().gueltigHeute()).isFalse();
        }
    }

    @Test
    void meldetUnbekannteSeriennummer() {
        assertThat(service.certificateBySerial("DEADBEEF")).isEmpty();
    }
}
