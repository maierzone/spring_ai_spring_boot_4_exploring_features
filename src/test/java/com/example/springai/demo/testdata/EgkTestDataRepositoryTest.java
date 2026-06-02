package com.example.springai.demo.testdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.springai.demo.testdata.EgkTestDataRepository.Stats;
import com.example.springai.demo.testdata.TestDataGenerator.Dataset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * Integrationstest gegen eine eingebettete H2-DB: stellt sicher, dass
 * {@code schema-egk.sql} laedt und der Batch-Insert samt Auswertung funktioniert.
 * Validiert damit zugleich die H2-/Postgres-Kompatibilitaet des Schemas.
 */
class EgkTestDataRepositoryTest {

    private EmbeddedDatabase db;
    private EgkTestDataRepository repository;
    private final TestDataGenerator generator = new TestDataGenerator();

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema-egk.sql")
                .build();
        repository = new EgkTestDataRepository(new JdbcTemplate(db));
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void schreibtUndZaehltDatensatz() {
        Dataset d = generator.generate(50, 42L);
        repository.replaceAll(d);

        Stats stats = repository.stats();
        assertThat(stats.versicherte()).isEqualTo(50);
        assertThat(stats.egkKarten()).isEqualTo(50);
        assertThat(stats.krankenkassen()).isEqualTo(d.krankenkassen().size());
        assertThat(stats.zertifikate()).isEqualTo(d.zertifikate().size());
        assertThat(stats.diagnosen()).isEqualTo(d.diagnosen().size());

        // Status-Verteilungen summieren sich auf die Gesamtzahl.
        assertThat(stats.zertifikateNachStatus().values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(stats.zertifikate());
        assertThat(stats.egkKartenNachStatus().values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(stats.egkKarten());
    }

    @Test
    void replaceAllErsetztDenBestandStattAnzuhaengen() {
        repository.replaceAll(generator.generate(30, 1L));
        repository.replaceAll(generator.generate(20, 2L));

        // Nach dem zweiten Lauf darf nur der zweite Datensatz uebrig sein.
        assertThat(repository.stats().versicherte()).isEqualTo(20);
    }
}
