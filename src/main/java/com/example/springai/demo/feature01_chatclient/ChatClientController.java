package com.example.springai.demo.feature01_chatclient;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FEATURE 1 – ChatClient (das Kern-Feature von Spring AI).
 *
 * <p>Der {@link ChatClient} ist die zentrale, "fluent" API, um mit einem
 * Sprachmodell zu sprechen: Prompt zusammenbauen → {@code call()} → Antwort lesen.
 * Er abstrahiert vom konkreten Provider (hier Anthropic), sodass derselbe Code
 * mit OpenAI, Ollama usw. funktionieren würde.</p>
 *
 * <p>Beispiel: {@code GET /api/chat?message=Erklaere Spring AI in einem Satz}</p>
 */
@RestController
public class ChatClientController {

    private final ChatClient chatClient;

    /**
     * Spring AI stellt automatisch einen {@link ChatClient.Builder} bereit, der
     * bereits mit dem konfigurierten {@code ChatModel} (Anthropic) verdrahtet ist.
     * Wir setzen hier zusätzlich eine System-Nachricht als Standardverhalten.
     */
    public ChatClientController(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("Du bist ein hilfreicher Assistent und antwortest knapp auf Deutsch.")
                .build();
    }

    @GetMapping("/api/chat")
    public String chat(@RequestParam(defaultValue = "Sag Hallo.") String message) {
        // prompt() -> user(...) baut die Anfrage; call() führt sie synchron aus;
        // content() liefert den reinen Text der Modellantwort.
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
