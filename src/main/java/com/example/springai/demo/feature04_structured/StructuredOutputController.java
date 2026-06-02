package com.example.springai.demo.feature04_structured;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
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
}
