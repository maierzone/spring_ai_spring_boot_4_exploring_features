package com.example.springai.demo.feature13_evaluation;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FEATURE 13 – Evaluation (LLM-as-a-Judge gegen Halluzinationen).
 *
 * <p>Wie testet man eine nicht-deterministische KI-Antwort? Spring AI liefert dafuer
 * {@code Evaluator}-Implementierungen, die ein (ggf. zweites) Modell als <em>Richter</em>
 * einsetzen. Der {@link RelevancyEvaluator} prueft, ob eine Antwort durch den
 * mitgegebenen Kontext (z.&nbsp;B. die von einem RAG-Schritt gelieferten Dokumente)
 * gedeckt ist – also <em>relevant und gegroundet</em> statt frei halluziniert.</p>
 *
 * <p>Das ist der natuerliche Abschluss einer RAG-Pipeline (Feature 7): Erst Kontext
 * abrufen, dann antworten, dann automatisiert bewerten – und z.&nbsp;B. in der CI als
 * Qualitaets-Gate verwenden.</p>
 *
 * <p>Beispiel:
 * {@code GET /api/evaluate/relevancy?question=Was ist RAG?&context=RAG kombiniert Retrieval mit Generation.&answer=RAG ruft passende Dokumente ab und nutzt sie als Kontext.}</p>
 */
@RestController
public class EvaluationController {

    private final RelevancyEvaluator relevancyEvaluator;

    public EvaluationController(ChatClient.Builder builder) {
        // Der Evaluator baut sich aus dem ChatClient.Builder seinen eigenen "Richter".
        this.relevancyEvaluator = RelevancyEvaluator.builder().chatClientBuilder(builder).build();
    }

    @GetMapping("/api/evaluate/relevancy")
    public EvaluationResult evaluateRelevancy(
            @RequestParam String question,
            @RequestParam String context,
            @RequestParam String answer) {

        List<Document> contextData = List.of(new Document(context));
        EvaluationRequest request = new EvaluationRequest(question, contextData, answer);

        EvaluationResponse response = relevancyEvaluator.evaluate(request);
        return new EvaluationResult(response.isPass(), response.getFeedback());
    }

    /** Schlankes Ergebnis-DTO fuer den Endpunkt. */
    public record EvaluationResult(boolean pass, String feedback) {
    }
}
