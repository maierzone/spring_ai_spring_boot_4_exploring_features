package com.example.springai.demo.feature04_structured;

import java.util.List;

import com.example.springai.demo.feature04_structured.TicketRepository.CategoryCount;
import com.example.springai.demo.feature10_advisors.ModelPricing;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class StructuredOutputController {

    private final ChatClient chatClient;
    private final TicketRepository ticketRepository;

    public StructuredOutputController(ChatClient.Builder builder, TicketRepository ticketRepository) {
        this.chatClient = builder.build();
        this.ticketRepository = ticketRepository;
    }

    /**
     * Ziel-Datenstruktur. Aus diesem Record leitet Spring AI automatisch ein
     * JSON-Schema ab, das dem Modell als Format-Anweisung mitgegeben wird.
     */
    public record Recipe(String title, List<String> ingredients, List<String> steps) {
    }

    @GetMapping("/api/recipe")
    public Recipe recipe(@RequestParam(defaultValue = "Pfannkuchen") String dish) {
        return chatClient.prompt()
                .user("Erstelle ein einfaches Rezept fuer: " + dish)
                // entity(Recipe.class) hängt die Format-Anweisung an und parst die
                // JSON-Antwort des Modells direkt in einen Recipe-Record.
                .call()
                .entity(Recipe.class);
    }

    // ------------------------------------------------------------------------
    // Praxisnaher Developer-Use-Case: Klassifikation/Extraktion.
    // ------------------------------------------------------------------------
    // Der wohl häufigste produktive Einsatz von Structured Output ist nicht das
    // Generieren von Inhalten, sondern das *Strukturieren von unstrukturiertem
    // Input*: aus einem frei formulierten Support-Ticket eine typisierte,
    // weiterverarbeitbare Analyse machen (Kategorie, Priorität, Sentiment, ...).
    // Durch die Enums ist die Ausgabe auf gültige Werte beschränkt – das
    // resultierende Objekt lässt sich direkt in einem Switch oder einer
    // Routing-Logik verwenden, ohne String-Vergleiche.

    /** Fachliche Kategorie eines Tickets. Enums erzwingen ein geschlossenes Werteset. */
    public enum Category { BUG, FEATURE_REQUEST, QUESTION, BILLING, OTHER }

    /** Dringlichkeit – steuert z.B. das Routing/SLA. */
    public enum Priority { LOW, MEDIUM, HIGH, URGENT }

    /** Erkannte Stimmung des Kunden. */
    public enum Sentiment { POSITIVE, NEUTRAL, NEGATIVE }

    /**
     * Zielstruktur der Analyse. Aus diesem Record samt Enums leitet Spring AI das
     * JSON-Schema ab, an das sich das Modell halten soll.
     */
    public record TicketAnalysis(
            Category category,
            Priority priority,
            Sentiment customerSentiment,
            String summary) {
    }

    @PostMapping("/api/tickets/analyze")
    public AnalyzeResponse analyzeTicket(@RequestBody String ticketText) {
        // Strukturierte Ausgabe manuell ueber den BeanOutputConverter (statt .entity(...)):
        // so kommen typisierte Analyse UND ChatResponse (Token-Verbrauch) aus EINEM Aufruf.
        BeanOutputConverter<TicketAnalysis> converter = new BeanOutputConverter<>(TicketAnalysis.class);
        long startNanos = System.nanoTime();
        ChatResponse response = chatClient.prompt()
                .user(u -> u.text("Analysiere das folgende Support-Ticket und fuelle die "
                        + "Felder aus:\n\n{ticket}\n\n{format}")
                        .param("ticket", ticketText)
                        .param("format", converter.getFormat()))
                .call()
                .chatResponse();
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
        TicketAnalysis analysis = converter.convert(response.getResult().getOutput().getText());
        // Feature E: die typisierte Analyse landet in PostgreSQL und ist damit
        // auswertbar (siehe /api/tickets/stats).
        ticketRepository.save(analysis, ticketText);
        return new AnalyzeResponse(analysis, AnalyzeMetrics.from(usageOf(response), modelOf(response), latencyMs));
    }

    /** Token-Verbrauch aus den Antwort-Metadaten lesen, sofern vorhanden. */
    private static Usage usageOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        return response.getMetadata().getUsage();
    }

    /** Modell-Id aus den Antwort-Metadaten lesen (fuer die Kostenabschaetzung). */
    private static String modelOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        return response.getMetadata().getModel();
    }

    /** Antwort der Ticket-Analyse: die typisierte Analyse plus die Ressourcen-Metriken. */
    public record AnalyzeResponse(TicketAnalysis analysis, AnalyzeMetrics metrics) {
    }

    /** Ressourcenverbrauch fuer die Badge-Anzeige (gleiche Felder wie bei den uebrigen Features). */
    public record AnalyzeMetrics(Integer inputTokens, Integer outputTokens, Integer totalTokens,
                                 long latencyMs, Double costUsd) {

        static AnalyzeMetrics from(Usage usage, String model, long latencyMs) {
            if (usage == null) {
                return new AnalyzeMetrics(null, null, null, latencyMs, null);
            }
            Integer inputTokens = usage.getPromptTokens();
            Integer outputTokens = usage.getCompletionTokens();
            Double costUsd = (inputTokens != null && outputTokens != null)
                    ? ModelPricing.costUsd(model, inputTokens, outputTokens) : null;
            return new AnalyzeMetrics(inputTokens, outputTokens, usage.getTotalTokens(), latencyMs, costUsd);
        }
    }

    /**
     * Analytics-Endpunkt: liefert die Anzahl der bisher analysierten Tickets je
     * Kategorie (SQL {@code GROUP BY}). Zeigt, wie aus persistierter KI-Extraktion
     * unmittelbar Geschaeftskennzahlen werden.
     */
    @GetMapping("/api/tickets/stats")
    public List<CategoryCount> ticketStats() {
        return ticketRepository.countByCategory();
    }
}
