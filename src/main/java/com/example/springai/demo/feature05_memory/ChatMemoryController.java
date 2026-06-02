package com.example.springai.demo.feature05_memory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FEATURE 5 – Chat Memory (mehrstufige Konversation).
 *
 * <p>Ein LLM ist von Natur aus zustandslos: Es "erinnert" sich nicht an
 * vorherige Nachrichten. Der {@link MessageChatMemoryAdvisor} klinkt sich in jede
 * Anfrage ein, lädt den bisherigen Gesprächsverlauf aus dem {@link ChatMemory}
 * und hängt ihn an den Prompt an – getrennt je Konversations-ID.</p>
 *
 * <p>Beispiel-Dialog (gleiche conversationId verwenden):</p>
 * <pre>
 *   POST /api/memory/anna?message=Mein Name ist Anna.
 *   POST /api/memory/anna?message=Wie heisse ich?      -&gt; Modell kennt nun "Anna"
 * </pre>
 */
@RestController
public class ChatMemoryController {

    private final ChatClient chatClient;

    /**
     * Der Memory-Advisor wird als Default-Advisor registriert, sodass er bei jeder
     * Anfrage dieses ChatClients greift.
     */
    public ChatMemoryController(ChatClient.Builder builder, ChatMemory chatMemory) {
        this.chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @PostMapping("/api/memory/{conversationId}")
    public String chat(@PathVariable String conversationId,
                       @RequestParam String message) {
        return chatClient.prompt()
                .user(message)
                // Über die Konversations-ID weiß der Advisor, welcher Verlauf
                // geladen und fortgeschrieben werden muss.
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
}
