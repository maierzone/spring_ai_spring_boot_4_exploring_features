package com.example.springai.demo.feature04_structured;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.example.springai.demo.feature04_structured.StructuredOutputController.Category;
import com.example.springai.demo.feature04_structured.StructuredOutputController.Priority;
import com.example.springai.demo.feature04_structured.StructuredOutputController.Sentiment;
import com.example.springai.demo.feature04_structured.StructuredOutputController.TicketAnalysis;

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
 * Testet das Structured-Output-Feature offline, indem das {@link ChatModel} gemockt
 * wird und eine feste JSON-Antwort liefert. Verifiziert wird, dass Spring AI diese
 * JSON-Antwort korrekt in den typisierten {@link TicketAnalysis}-Record überführt –
 * inklusive Mapping der Strings auf die Enum-Konstanten.
 */
class StructuredOutputControllerTest {

    @Test
    void analysiertTicketInTypisiertesObjekt() {
        // Modellantwort simulieren: genau das JSON, das ein echtes Modell liefern würde.
        String json = """
                {
                  "category": "BUG",
                  "priority": "HIGH",
                  "customerSentiment": "NEGATIVE",
                  "summary": "Login schlaegt nach Update fehl."
                }
                """;

        ChatModel chatModel = Mockito.mock(ChatModel.class);
        // Der ChatClient liest beim Prompt-Aufbau die Default-Optionen des Modells –
        // beim Mock müssen wir dafür ein nicht-null Objekt bereitstellen.
        when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));

        // Persistenz wird gemockt: der Test bleibt reiner Logik-Test (kein JDBC).
        TicketRepository ticketRepository = Mockito.mock(TicketRepository.class);

        // Controller mit einem ChatClient, der auf dem gemockten Modell aufsetzt.
        StructuredOutputController controller =
                new StructuredOutputController(ChatClient.builder(chatModel), ticketRepository);

        String ticketText = "Nach dem Update kann ich mich nicht mehr einloggen!";
        TicketAnalysis result = controller.analyzeTicket(ticketText);

        assertThat(result.category()).isEqualTo(Category.BUG);
        assertThat(result.priority()).isEqualTo(Priority.HIGH);
        assertThat(result.customerSentiment()).isEqualTo(Sentiment.NEGATIVE);
        assertThat(result.summary()).contains("Login");

        // Feature E: die typisierte Analyse wird zur Persistenz weitergereicht.
        verify(ticketRepository).save(result, ticketText);
    }
}
