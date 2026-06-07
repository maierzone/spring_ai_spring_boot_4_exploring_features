package com.example.springai.demo.feature01_chatclient;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
