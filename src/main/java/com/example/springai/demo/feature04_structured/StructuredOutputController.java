package com.example.springai.demo.feature04_structured;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FEATURE 4 – Structured Output (typisierte Antworten).
 *
 * <p>LLMs liefern standardmäßig Freitext. Oft braucht man aber ein strukturiertes
 * Java-Objekt. Spring AI kann das Modell anweisen, JSON in einem bestimmten Schema
 * zu liefern, und das Ergebnis automatisch in einen Java-Typ deserialisieren –
 * über {@code .entity(...)}. Damit wird aus "KI-Text" ein sauber typisierter
 * Rückgabewert, den der Rest der Anwendung weiterverarbeiten kann.</p>
 *
 * <p>Beispiel: {@code GET /api/recipe?dish=Pfannkuchen} liefert JSON im Schema
 * des {@link Recipe}-Records.</p>
 */
@RestController
public class StructuredOutputController {

    private final ChatClient chatClient;

    public StructuredOutputController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
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
    public TicketAnalysis analyzeTicket(@RequestBody String ticketText) {
        return chatClient.prompt()
                .user(u -> u.text("Analysiere das folgende Support-Ticket und fuelle die "
                        + "Felder aus:\n\n{ticket}").param("ticket", ticketText))
                // Das Ergebnis wird direkt in den typisierten Record geparst –
                // inklusive Mapping der Strings auf die Enum-Konstanten.
                .call()
                .entity(TicketAnalysis.class);
    }
}
