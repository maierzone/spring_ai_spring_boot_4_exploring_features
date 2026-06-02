package com.example.springai.demo.feature02_streaming;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

/**
 * FEATURE 2 – Streaming-Antworten.
 *
 * <p>Statt auf die komplette Antwort zu warten ({@code call()}), liefert
 * {@code stream()} die Tokens des Modells als reaktiven {@link Flux} – Stück für
 * Stück, so wie sie generiert werden. Das ist die Grundlage für den typischen
 * "Schreibmaschinen-Effekt" in Chat-Oberflächen.</p>
 *
 * <p>Wir geben den Stream als Server-Sent-Events (SSE) aus:
 * {@code GET /api/stream?message=Zaehle von 1 bis 10}</p>
 */
@RestController
public class StreamingController {

    private final ChatClient chatClient;

    public StreamingController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    // produces=text/event-stream sorgt dafür, dass die Antwort als SSE-Strom
    // übertragen wird und der Client die Teilstücke fortlaufend empfängt.
    @GetMapping(value = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam(defaultValue = "Erzaehl einen kurzen Witz.") String message) {
        return chatClient.prompt()
                .user(message)
                .stream()
                .content();
    }
}
