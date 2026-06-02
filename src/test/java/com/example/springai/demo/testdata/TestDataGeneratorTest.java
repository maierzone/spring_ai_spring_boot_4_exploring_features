package com.example.springai.demo.testdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import com.example.springai.demo.testdata.TestDataGenerator.Dataset;
import com.example.springai.demo.testdata.TestDataGenerator.Versicherter;
import com.example.springai.demo.testdata.TestDataGenerator.Zertifikat;

import org.junit.jupiter.api.Test;

/**
 * Offline-Tests des Generators: Reproduzierbarkeit, referentielle Integritaet,
 * korrekte KVNR-Pruefziffern und konsistente Zertifikatsstatus – alles ohne DB.
 */
class TestDataGeneratorTest {

    private final TestDataGenerator generator = new TestDataGenerator();

    @Test
    void gleicherSeedErzeugtIdentischeDaten() {
        Dataset a = generator.generate(200, 42L);
        Dataset b = generator.generate(200, 42L);
        // Records implementieren equals -> Listenvergleich prueft Feld fuer Feld.
        assertThat(a).isEqualTo(b);
    }

    @Test
    void andererSeedErzeugtAndereDaten() {
        Dataset a = generator.generate(200, 1L);
        Dataset b = generator.generate(200, 2L);
        assertThat(a.versicherte()).isNotEqualTo(b.versicherte());
    }

    @Test
    void volumenSkaliertMitAnzahl() {
        Dataset d = generator.generate(200, 42L);
        assertThat(d.versicherte()).hasSize(200);
        assertThat(d.karten()).hasSize(200); // eine eGK je Versichertem
        // je Karte 2 eGK-Zertifikate + je Leistungserbringer 1 Profilzertifikat
        int le = d.leistungserbringer().size();
        assertThat(d.zertifikate()).hasSize(le + 200 * 2);
        assertThat(d.diagnosen().size()).isBetween(200, 200 * 4);
    }

    @Test
    void alleKvnrTragenKorrektePruefziffer() {
        Dataset d = generator.generate(500, 42L);
        for (Versicherter v : d.versicherte()) {
            String kvnr = v.kvnr();
            assertThat(kvnr).hasSize(10);
            char letter = kvnr.charAt(0);
            int erwartet = CheckDigits.kvnrPruefziffer(letter, kvnr.substring(1, 9));
            int tatsaechlich = kvnr.charAt(9) - '0';
            assertThat(tatsaechlich).isEqualTo(erwartet);
        }
    }

    @Test
    void fremdschluesselSindAlleAufloesbar() {
        Dataset d = generator.generate(300, 7L);
        Set<Long> kassen = ids(d.krankenkassen().stream().map(k -> k.id()));
        Set<Long> versicherte = ids(d.versicherte().stream().map(v -> v.id()));
        Set<Long> le = ids(d.leistungserbringer().stream().map(l -> l.id()));
        Set<Long> cas = ids(d.caZertifikate().stream().map(c -> c.id()));

        assertThat(d.versicherte()).allMatch(v -> kassen.contains(v.krankenkasseId()));
        assertThat(d.karten()).allMatch(k -> versicherte.contains(k.versicherteId()));
        assertThat(d.diagnosen()).allMatch(dg ->
                versicherte.contains(dg.versicherteId()) && le.contains(dg.leistungserbringerId()));
        assertThat(d.zertifikate()).allMatch(z -> cas.contains(z.caId()));
    }

    @Test
    void zertifikatsstatusIstKonsistentMitDaten() {
        Dataset d = generator.generate(400, 42L);
        for (Zertifikat z : d.zertifikate()) {
            switch (z.status()) {
                case "VALID" -> {
                    assertThat(z.notAfter()).isAfterOrEqualTo(TestDataGenerator.STICHTAG);
                    assertThat(z.revocationDatum()).isNull();
                }
                case "EXPIRED" -> assertThat(z.notAfter()).isBefore(TestDataGenerator.STICHTAG);
                case "REVOKED" -> assertThat(z.revocationDatum()).isNotNull();
                default -> throw new AssertionError("Unerwarteter Status: " + z.status());
            }
        }
        // Alle drei Status sollen im Datensatz tatsaechlich vorkommen.
        Set<String> status = d.zertifikate().stream().map(Zertifikat::status).collect(Collectors.toSet());
        assertThat(status).containsExactlyInAnyOrder("VALID", "EXPIRED", "REVOKED");
    }

    private static Set<Long> ids(java.util.stream.Stream<Long> stream) {
        return stream.collect(Collectors.toSet());
    }
}
