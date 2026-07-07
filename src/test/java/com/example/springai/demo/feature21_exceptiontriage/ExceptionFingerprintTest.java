package com.example.springai.demo.feature21_exceptiontriage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Verifiziert, dass derselbe Fehler denselben Fingerabdruck ergibt (Gruppierung/Dedup)
 * und dass Typ sowie eigener Ausloese-Frame im Schluessel stecken.
 */
class ExceptionFingerprintTest {

    @Test
    void gleicheUrsacheErgibtGleichenFingerprint() {
        String a = ExceptionFingerprint.of(boom());
        String b = ExceptionFingerprint.of(boom());
        assertThat(a).isEqualTo(b);
    }

    @Test
    void enthaeltTypUndEigenenFrame() {
        String fp = ExceptionFingerprint.of(boom());
        assertThat(fp)
                .startsWith("IllegalStateException@")
                .contains("ExceptionFingerprintTest")
                .contains("boom");
    }

    private static IllegalStateException boom() {
        return new IllegalStateException("kaputt");
    }
}
