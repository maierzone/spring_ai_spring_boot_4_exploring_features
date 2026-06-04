package com.example.springai.demo.feature19_moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Testet den {@link ModerationGuardrailAdvisor} vollstaendig offline: Der
 * {@link ContentModerator} ist ein deterministisches Lambda (kein LLM), die
 * nachgelagerte Kette ist gemockt. Verifiziert werden beide Schutzrichtungen.
 */
class ModerationGuardrailAdvisorTest {

    /** Markiert Text als unzulaessig, sobald er das Reizwort enthaelt – sonst unbedenklich. */
    private static final ContentModerator KEYWORD_MODERATOR = text ->
            text.toLowerCase().contains("boese")
                    ? new ModerationVerdict(true, List.of("hate"), "Reizwort erkannt")
                    : ModerationVerdict.safe();

    @Test
    void blockiertUnzulaessigeEingabeVorDemModellaufruf() {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        ChatClientRequest request = new ChatClientRequest(new Prompt("Das ist eine boese Nachricht"), Map.of());

        ChatClientResponse result =
                new ModerationGuardrailAdvisor(KEYWORD_MODERATOR).adviseCall(request, chain);

        // Die Kette (und damit das Modell) darf gar nicht erst aufgerufen werden.
        verify(chain, never()).nextCall(any());
        assertThat(result.chatResponse().getResult().getOutput().getText())
                .isEqualTo(ModerationGuardrailAdvisor.BLOCKED_INPUT);
        assertThat(result.context()).containsEntry("moderation.flagged", true);
    }

    @Test
    void laesstUnbedenklicheKonversationDurch() {
        ChatClientResponse downstream = new ChatClientResponse(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Eine harmlose Antwort.")))),
                Map.of());
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(downstream);

        ChatClientRequest request = new ChatClientRequest(new Prompt("Eine harmlose Frage"), Map.of());

        ChatClientResponse result =
                new ModerationGuardrailAdvisor(KEYWORD_MODERATOR).adviseCall(request, chain);

        verify(chain).nextCall(any(ChatClientRequest.class));
        assertThat(result).isSameAs(downstream);
    }

    @Test
    void filtertUnzulaessigeModellantwort() {
        ChatClientResponse downstream = new ChatClientResponse(
                new ChatResponse(List.of(new Generation(new AssistantMessage("eine boese Antwort")))),
                Map.of());
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(downstream);

        ChatClientRequest request = new ChatClientRequest(new Prompt("Eine harmlose Frage"), Map.of());

        ChatClientResponse result =
                new ModerationGuardrailAdvisor(KEYWORD_MODERATOR).adviseCall(request, chain);

        assertThat(result.chatResponse().getResult().getOutput().getText())
                .isEqualTo(ModerationGuardrailAdvisor.BLOCKED_OUTPUT);
    }
}
