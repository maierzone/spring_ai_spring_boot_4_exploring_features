package com.example.springai.demo.feature21_exceptiontriage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.springai.demo.feature21_exceptiontriage.ExceptionTriage.Category;
import com.example.springai.demo.feature21_exceptiontriage.ExceptionTriage.Severity;
import com.example.springai.demo.feature21_exceptiontriage.ExceptionTriageStore.Registration;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Testet den globalen Handler rein synchron – ohne LLM. Store und Orchestrator sind
 * gemockt; geprueft werden die drei Design-Zusagen: 4xx ohne Triage, 5xx-Erstauftreten
 * stoesst die Analyse an, und ein Cache-Hit reichert die Antwort mit der KI-Meldung an.
 */
class AiExceptionHandlerTest {

    private final ExceptionTriageStore store = Mockito.mock(ExceptionTriageStore.class);
    private final ExceptionTriageOrchestrator orchestrator = Mockito.mock(ExceptionTriageOrchestrator.class);
    private final AiExceptionHandler handler = new AiExceptionHandler(store, orchestrator);

    private static MockHttpServletRequest request() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/errors/trigger");
        return req;
    }

    @Test
    void clientFehlerWirdNichtTriagiert() {
        ProblemDetail problem = handler.handle(new IllegalArgumentException("bad"), request());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        verify(store, never()).record(any());
        verify(orchestrator, never()).triageAsync(any());
        // Interne Meldung leakt nicht.
        assertThat(problem.getDetail()).doesNotContain("bad");
    }

    @Test
    void serverFehlerErstauftretenStoesstAnalyseAn() {
        when(store.record(any())).thenReturn(new Registration(true, null));

        ProblemDetail problem = handler.handle(new IllegalStateException("boom"), request());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).isEqualTo("Es ist ein interner Fehler aufgetreten.");
        verify(orchestrator, times(1)).triageAsync(any());
        assertThat(problem.getProperties()).containsKey("errorId").containsKey("fingerprint");
    }

    @Test
    void cacheHitReichertMitKiMeldungAn() {
        ExceptionTriage triage = new ExceptionTriage(
                Category.EXTERNAL_DEPENDENCY, Severity.HIGH, true,
                "Provider down.", "Retry pruefen.", "Dienst kurz nicht verfuegbar, bitte erneut versuchen.");
        when(store.record(any())).thenReturn(new Registration(false, triage));

        ProblemDetail problem = handler.handle(new IllegalStateException("boom"), request());

        // Keine erneute Analyse bei Wiederholung.
        verify(orchestrator, never()).triageAsync(any());
        assertThat(problem.getDetail()).isEqualTo("Dienst kurz nicht verfuegbar, bitte erneut versuchen.");
        assertThat(problem.getProperties())
                .containsEntry("category", Category.EXTERNAL_DEPENDENCY)
                .containsEntry("severity", Severity.HIGH)
                .containsEntry("retriable", true);
    }
}
