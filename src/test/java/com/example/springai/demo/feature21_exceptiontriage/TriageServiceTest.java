package com.example.springai.demo.feature21_exceptiontriage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import com.example.springai.demo.feature21_exceptiontriage.ExceptionTriage.Category;
import com.example.springai.demo.feature21_exceptiontriage.ExceptionTriage.Severity;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Testet die KI-Triage offline: das {@link ChatModel} wird gemockt und liefert festes
 * JSON, das Spring AI in einen {@link ExceptionTriage}-Record ueberfuehrt. Zusaetzlich
 * wird der Rekursions-/Fehlerschutz geprueft (Modellfehler → deterministischer Fallback).
 */
class TriageServiceTest {

    private static ExceptionContext ctx() {
        return ExceptionContext.capture(new RuntimeException("boom"), "eid-1", "/api/x", "GET");
    }

    @Test
    void ueberfuehrtModellantwortInTypisierteTriage() {
        String json = """
                {
                  "category": "EXTERNAL_DEPENDENCY",
                  "severity": "HIGH",
                  "retriable": true,
                  "probableRootCause": "LLM-Provider nicht erreichbar.",
                  "suggestedAction": "Status des Providers pruefen, Retry aktivieren.",
                  "userFacingMessage": "Der Dienst ist gerade nicht verfuegbar. Bitte spaeter erneut versuchen."
                }
                """;

        ChatModel chatModel = Mockito.mock(ChatModel.class);
        when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));

        TriageService service = new TriageService(ChatClient.builder(chatModel));
        ExceptionTriage triage = service.triage(ctx());

        assertThat(triage.category()).isEqualTo(Category.EXTERNAL_DEPENDENCY);
        assertThat(triage.severity()).isEqualTo(Severity.HIGH);
        assertThat(triage.retriable()).isTrue();
        assertThat(triage.userFacingMessage()).contains("nicht verfuegbar");
    }

    @Test
    void modellfehlerFuehrtZuDeterministischemFallback() {
        ChatModel chatModel = Mockito.mock(ChatModel.class);
        when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("provider down"));

        TriageService service = new TriageService(ChatClient.builder(chatModel));
        ExceptionTriage triage = service.triage(ctx());

        // Kein erneuter Aufruf, kein Durchreichen – deterministischer Fallback.
        assertThat(triage.category()).isEqualTo(Category.PROGRAMMING_BUG);
        assertThat(triage.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(triage.retriable()).isFalse();
    }
}
