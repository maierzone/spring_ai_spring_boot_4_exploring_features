package com.example.springai.demo.feature19_moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

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
 * Testet die Claude-as-Classifier-Implementierung offline: Das {@link ChatModel} wird
 * gemockt und liefert das JSON, das ein echtes Modell als Klassifikation zurueckgaebe.
 * Verifiziert wird, dass Spring AI dieses JSON via Structured Output korrekt in einen
 * {@link ModerationVerdict} ueberfuehrt.
 */
class ClaudeContentModeratorTest {

    @Test
    void parstFlaggedUrteilAusModellantwort() {
        String json = """
                {
                  "flagged": true,
                  "categories": ["hate", "harassment"],
                  "reason": "Beleidigende Sprache."
                }
                """;

        ChatModel chatModel = Mockito.mock(ChatModel.class);
        when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));

        ClaudeContentModerator moderator = new ClaudeContentModerator(ChatClient.builder(chatModel));

        ModerationVerdict verdict = moderator.moderate("irgendein beleidigender Text");

        assertThat(verdict.flagged()).isTrue();
        assertThat(verdict.categories()).containsExactly("hate", "harassment");
        assertThat(verdict.reason()).contains("Beleidigende");
    }

    @Test
    void leererTextWirdOhneModellaufrufAlsUnbedenklichBehandelt() {
        // Kein stubbing noetig: Bei leerem Text darf das Modell gar nicht erst gefragt werden.
        ChatModel chatModel = Mockito.mock(ChatModel.class);
        ClaudeContentModerator moderator = new ClaudeContentModerator(ChatClient.builder(chatModel));

        ModerationVerdict verdict = moderator.moderate("   ");

        assertThat(verdict.flagged()).isFalse();
        assertThat(verdict.categories()).isEmpty();
    }
}
