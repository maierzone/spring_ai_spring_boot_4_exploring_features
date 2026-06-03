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
 * Testet den abgesicherten Text-to-SQL-Fallback gegen H2: gueltige SELECTs liefern
 * Zeilen, verbotene Befehle werden mit Klartext abgelehnt (statt ausgefuehrt).
 */
class ReadOnlySqlToolTest {

    private EmbeddedDatabase db;
    private ReadOnlySqlTool tool;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema-egk.sql")
                .build();
        new EgkTestDataRepository(new JdbcTemplate(db))
                .replaceAll(new TestDataGenerator().generate(40, 7L));
        tool = new ReadOnlySqlTool(db);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void fuehrtGueltigesSelectAus() {
        String out = tool.fuehreSelectAus("SELECT COUNT(*) AS anzahl FROM versicherte");
        // Spaltennamen kommen je nach DB in Gross-/Kleinschreibung zurueck.
        assertThat(out).containsIgnoringCase("anzahl=40");
    }

    @Test
    void lehntSchreibendeBefehleAb() {
        String out = tool.fuehreSelectAus("DELETE FROM versicherte");
        assertThat(out).startsWith("Abfrage abgelehnt");
        // Daten sind unveraendert geblieben.
        assertThat(tool.fuehreSelectAus("SELECT COUNT(*) AS n FROM versicherte"))
                .containsIgnoringCase("n=40");
    }
}
