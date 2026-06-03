package com.example.springai.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Einstiegspunkt der Spring-AI-Demo.
 *
 * <p>Die Anwendung demonstriert die "Top X" Spring-AI-Features jeweils als
 * eigenen, ausführlich kommentierten REST-Endpunkt. Als LLM-Provider ist
 * Anthropic (Claude) konfiguriert; für die echte Nutzung muss die
 * Umgebungsvariable {@code ANTHROPIC_API_KEY} gesetzt sein.</p>
 *
 * <p>Die Tests dieser Anwendung laufen vollständig offline (ohne API-Key),
 * indem entweder reine Logik getestet oder das {@code ChatModel} gemockt
 * wird – siehe die Klassen unter {@code src/test}.</p>
 */
@SpringBootApplication
public class SpringAiDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiDemoApplication.class, args);
    }
}
