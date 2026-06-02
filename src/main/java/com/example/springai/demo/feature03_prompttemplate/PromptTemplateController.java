package com.example.springai.demo.feature03_prompttemplate;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FEATURE 3 – Prompt Templates.
 *
 * <p>Prompts enthalten in der Praxis variable Anteile (Benutzer-Eingaben,
 * Kontext, Parameter). {@link PromptTemplate} trennt die feste "Vorlage" mit
 * Platzhaltern (<code>{name}</code>) sauber von den eingesetzten Werten – das ist
 * lesbarer und weniger fehleranfällig als String-Verkettung und reduziert das
 * Risiko von Prompt-Injection durch unkontrollierte Konkatenation.</p>
 *
 * <p>Beispiel: {@code GET /api/joke?topic=Katzen&language=Englisch}</p>
 */
@RestController
public class PromptTemplateController {

    private final ChatClient chatClient;

    public PromptTemplateController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/api/joke")
    public String joke(@RequestParam(defaultValue = "Programmierer") String topic,
                       @RequestParam(defaultValue = "Deutsch") String language) {

        // Vorlage mit zwei Platzhaltern. Die Werte werden erst beim render() eingesetzt.
        PromptTemplate template = new PromptTemplate(
                "Erzaehle einen kurzen, originellen Witz ueber das Thema {topic}. "
                        + "Antworte ausschliesslich auf {language}.");

        String prompt = template.render(Map.of(
                "topic", topic,
                "language", language));

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}
