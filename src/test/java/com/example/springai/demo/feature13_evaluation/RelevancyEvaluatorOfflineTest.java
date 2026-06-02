package com.example.springai.demo.feature13_evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;

/**
 * Zeigt das Test-Muster fuer Evaluation OHNE echten Modellaufruf: Der "Richter" ist
 * ein Stub-{@link ChatModel}, der deterministisch YES bzw. NO liefert. So laesst sich
 * die Verdrahtung des {@link RelevancyEvaluator} (Prompt -> Urteil -> isPass) offline
 * und ohne API-Key pruefen – exakt so, wie ein echtes Eval-Gate in der CI aufgebaut
 * waere, nur mit einem echten Modell als Richter.
 */
class RelevancyEvaluatorOfflineTest {

    /** Minimaler ChatModel-Stub, der stets dieselbe Antwort zurueckgibt. */
    private static ChatModel fixedAnswer(String verdict) {
        return (Prompt prompt) -> new ChatResponse(List.of(new Generation(new AssistantMessage(verdict))));
    }

    private RelevancyEvaluator evaluatorReturning(String verdict) {
        return RelevancyEvaluator.builder()
                .chatClientBuilder(ChatClient.builder(fixedAnswer(verdict)))
                .build();
    }

    @Test
    void werteAlsRelevantWennRichterYesSagt() {
        EvaluationRequest request = new EvaluationRequest(
                "Was ist RAG?",
                List.of(new Document("RAG kombiniert Retrieval mit Generation.")),
                "RAG ruft passende Dokumente ab und nutzt sie als Kontext.");

        assertThat(evaluatorReturning("YES").evaluate(request).isPass()).isTrue();
    }

    @Test
    void werteAlsIrrelevantWennRichterNoSagt() {
        EvaluationRequest request = new EvaluationRequest(
                "Was ist RAG?",
                List.of(new Document("RAG kombiniert Retrieval mit Generation.")),
                "Das Wetter morgen wird sonnig.");

        assertThat(evaluatorReturning("NO").evaluate(request).isPass()).isFalse();
    }
}
