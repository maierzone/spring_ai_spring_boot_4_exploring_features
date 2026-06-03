package com.example.springai.demo.feature14_dbquery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Reine Logik-Tests fuer den SQL-Schutz: nur lesende Einzel-Abfragen passieren,
 * alles Schreibende/Mehrdeutige wird abgelehnt, fehlendes LIMIT wird ergaenzt.
 */
class SqlGuardTest {

    @Test
    void laesstEinfachesSelectDurchUndErgaenztLimit() {
        String out = SqlGuard.sanitize("SELECT COUNT(*) FROM versicherte");
        assertThat(out).isEqualTo("SELECT COUNT(*) FROM versicherte LIMIT " + SqlGuard.DEFAULT_LIMIT);
    }

    @Test
    void behaeltVorhandenesLimitBei() {
        String out = SqlGuard.sanitize("SELECT id FROM versicherte LIMIT 5");
        assertThat(out).isEqualTo("SELECT id FROM versicherte LIMIT 5");
    }

    @Test
    void entferntEinzelnesAbschliessendesSemikolon() {
        String out = SqlGuard.sanitize("SELECT 1 LIMIT 1;");
        assertThat(out).isEqualTo("SELECT 1 LIMIT 1");
    }

    @Test
    void erlaubtWithCte() {
        String out = SqlGuard.sanitize("WITH x AS (SELECT 1 AS n) SELECT n FROM x LIMIT 1");
        assertThat(out).startsWith("WITH");
    }

    @Test
    void lehntDmlAb() {
        assertThatThrownBy(() -> SqlGuard.sanitize("DELETE FROM versicherte"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlGuard.sanitize("UPDATE versicherte SET ort = 'x'"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlGuard.sanitize("DROP TABLE versicherte"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lehntDmlAlsZweitesStatementAb() {
        assertThatThrownBy(() -> SqlGuard.sanitize("SELECT 1; DROP TABLE versicherte"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lehntKommentareAb() {
        assertThatThrownBy(() -> SqlGuard.sanitize("SELECT 1 -- kommentar"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lehntNichtSelectAb() {
        assertThatThrownBy(() -> SqlGuard.sanitize("EXPLAIN SELECT 1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlGuard.sanitize("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
