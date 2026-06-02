package com.example.springai.demo.testdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Verifiziert die Pruefziffer-Algorithmen an manuell nachgerechneten Beispielen
 * sowie an einem bekannten Luhn-Referenzwert.
 */
class CheckDigitsTest {

    @Test
    void kvnrPruefzifferEntsprichtHandrechnung() {
        // Basis "01" (A) + "23456789", alternierende Gewichtung 1,2 mit Quersumme:
        // Summe = 43 -> Pruefziffer 3.
        assertThat(CheckDigits.kvnrPruefziffer('A', "23456789")).isEqualTo(3);
        assertThat(CheckDigits.kvnr('A', "23456789")).isEqualTo("A234567893");
    }

    @Test
    void luhnEntsprichtBekanntemReferenzwert() {
        // Klassisches Luhn-Beispiel: 7992739871 -> Pruefziffer 3 (=> 79927398713 ist gueltig).
        assertThat(CheckDigits.luhnPruefziffer("7992739871")).isEqualTo(3);
        assertThat(CheckDigits.mitLuhn("7992739871")).isEqualTo("79927398713");
    }

    @Test
    void ungueltigeEingabenWerdenAbgelehnt() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> CheckDigits.kvnrPruefziffer('a', "12345678"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> CheckDigits.kvnrPruefziffer('A', "123")); // zu kurz
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> CheckDigits.luhnPruefziffer("12x4"));
    }
}
