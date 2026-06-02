package com.example.springai.demo.feature10_advisors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Testet den selbst geschriebenen {@link MetricsLoggingAdvisor} isoliert: Die
 * restliche Advisor-Kette wird gemockt und liefert eine Antwort mit bekannter
 * Token-Nutzung. Verifiziert wird, dass der Advisor Latenz und Token-Anzahl in den
 * Antwort-Kontext schreibt.
 */
class MetricsLoggingAdvisorTest {

    @Test
    void schreibtLatenzUndTokensInDenKontext() {
        // Antwort der nachgelagerten Kette: Modellantwort mit Usage = 10/20/30 Tokens.
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .usage(new DefaultUsage(10, 20, 30))
                .build();
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage("Antwort"))), metadata);
        ChatClientResponse downstream = new ChatClientResponse(chatResponse, Map.of());

        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(downstream);

        ChatClientRequest request = new ChatClientRequest(new Prompt("Testfrage"), Map.of());

        ChatClientResponse result = new MetricsLoggingAdvisor().adviseCall(request, chain);

        assertThat(result.context()).containsEntry("metrics.totalTokens", 30);
        assertThat(result.context().get("metrics.latencyMs"))
                .isInstanceOf(Long.class)
                .satisfies(ms -> assertThat((Long) ms).isGreaterThanOrEqualTo(0L));
    }
}
