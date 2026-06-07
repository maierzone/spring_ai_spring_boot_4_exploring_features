package com.example.springai.demo.feature09_multimodal;

import java.io.IOException;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


@RestController
public class MultimodalController {

    private final ChatClient chatClient;

    public MultimodalController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @PostMapping("/api/multimodal")
    public String describe(@RequestParam("image") MultipartFile image,
                           @RequestParam(defaultValue = "Was ist auf diesem Bild zu sehen?") String message)
            throws IOException {

        // MIME-Typ der hochgeladenen Datei bestimmen (Fallback: image/png).
        MimeType mimeType = image.getContentType() != null
                ? MimeTypeUtils.parseMimeType(image.getContentType())
                : MimeTypeUtils.IMAGE_PNG;

        // Media kapselt Typ + Binärdaten des Bildes.
        Media media = Media.builder()
                .mimeType(mimeType)
                .data(image.getBytes())
                .build();

        // In der Benutzer-Nachricht werden Text UND Bild gemeinsam übergeben.
        return chatClient.prompt()
                .user(u -> u.text(message).media(media))
                .call()
                .content();
    }
}
