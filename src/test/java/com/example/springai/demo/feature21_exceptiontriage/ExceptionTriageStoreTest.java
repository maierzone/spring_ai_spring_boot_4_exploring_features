package com.example.springai.demo.feature21_exceptiontriage;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.springai.demo.feature21_exceptiontriage.ExceptionTriage.Category;
import com.example.springai.demo.feature21_exceptiontriage.ExceptionTriage.Severity;
import com.example.springai.demo.feature21_exceptiontriage.ExceptionTriageStore.Registration;

import org.junit.jupiter.api.Test;

/**
 * Kern der Dedup-Logik: nur das Erstauftreten meldet {@code firstOccurrence=true}
 * (→ genau eine Analyse), und nach {@link ExceptionTriageStore#complete} liefert der
 * Store die fertige Analyse als Cache-Hit mit.
 */
class ExceptionTriageStoreTest {

    private static final ExceptionTriage TRIAGE = new ExceptionTriage(
            Category.TRANSIENT, Severity.LOW, true, "ursache", "aktion", "bitte erneut versuchen");

    @Test
    void nurErstauftretenIstFirstOccurrence() {
        ExceptionTriageStore store = new ExceptionTriageStore();

        Registration first = store.record("FP");
        Registration second = store.record("FP");

        assertThat(first.firstOccurrence()).isTrue();
        assertThat(second.firstOccurrence()).isFalse();
    }

    @Test
    void nachCompleteKommtDieAnalyseAlsCacheHit() {
        ExceptionTriageStore store = new ExceptionTriageStore();
        store.record("FP");                 // Erstauftreten, noch keine Analyse
        assertThat(store.record("FP").triage()).isNull();

        store.complete("FP", TRIAGE);       // Analyse trifft (asynchron) ein

        Registration hit = store.record("FP");
        assertThat(hit.firstOccurrence()).isFalse();
        assertThat(hit.triage()).isEqualTo(TRIAGE);
    }

    @Test
    void snapshotZaehltVorfaelle() {
        ExceptionTriageStore store = new ExceptionTriageStore();
        store.record("FP");
        store.record("FP");
        store.record("OTHER");

        assertThat(store.snapshot()).hasSize(2);
        assertThat(store.snapshot().get(0).fingerprint()).isEqualTo("FP");
        assertThat(store.snapshot().get(0).count()).isEqualTo(2);
    }
}
