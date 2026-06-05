package com.example.springai.demo.feature19_moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
 * Regressionstest fuer die Controller-<b>Verdrahtung</b> (Feature 19).
 *
 * <p>Deckt die Luecke, die die isolierten Unit-Tests fuer {@link ClaudeContentModerator}
 * und {@link ModerationGuardrailAdvisor} offenliessen: Beide Clients wurden im Controller
 * aus <em>demselben</em> {@link ChatClient.Builder} gebaut. Da Spring AIs
 * {@code build()} die mutable Request-Spec mit dem gebauten Client teilt (keine Kopie),
 * landete der spaeter hinzugefuegte {@link ModerationGuardrailAdvisor} auch im
 * Moderator-Client – die Klassifikation moderierte sich selbst und lief in eine
 * Endlosrekursion ({@link StackOverflowError}, vgl. troubleshooting/FEATURE_19_MODERATION_BUG.md).</p>
 *
 * <p>Das {@link ChatModel} ist gemockt: deterministisch, offline, ohne API-Key.</p>
 */
class ModerationControllerTest {

    private static ChatModel chatModelReturning(String assistantText) {
        ChatModel chatModel = Mockito.mock(ChatModel.class);
        when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(assistantText)))));
        return chatModel;
    }

    @Test
    void unbedenklicheAnfrageLaeuftOhneEndlosrekursionDurch() {
        // Das Modell stuft jede Klassifikation als unbedenklich ein.
        ChatModel chatModel = chatModelReturning(
                "{\"flagged\": false, \"categories\": [], \"reason\": \"ok\"}");
        ModerationController controller = new ModerationController(ChatClient.builder(chatModel));

        // Vor dem Fix endete dieser Aufruf in einem StackOverflowError.
        assertThatCode(() -> controller.ask("Eine harmlose Frage"))
                .doesNotThrowAnyException();

        // Genau drei Modellaufrufe: Eingabe-Moderation, eigentliche Antwort, Antwort-Moderation.
        // Der Moderator-Client darf den Guardrail-Advisor nicht erben, sonst waeren es unendlich viele.
        verify(chatModel, times(3)).call(any(Prompt.class));
    }

    @Test
    void unzulaessigeEingabeWirdBlockiertOhneModellaufrufZuRekursieren() {
        // Das Modell stuft jede Klassifikation als unzulaessig ein.
        ChatModel chatModel = chatModelReturning(
                "{\"flagged\": true, \"categories\": [\"hate\"], \"reason\": \"Reizwort\"}");
        ModerationController controller = new ModerationController(ChatClient.builder(chatModel));

        String answer = controller.ask("Eine unzulaessige Eingabe");

        assertThat(answer).isEqualTo(ModerationGuardrailAdvisor.BLOCKED_INPUT);
        // Nur die Eingabe-Moderation laeuft; die Kette (und damit ein weiterer Modellaufruf) entfaellt.
        verify(chatModel, times(1)).call(any(Prompt.class));
    }
}
