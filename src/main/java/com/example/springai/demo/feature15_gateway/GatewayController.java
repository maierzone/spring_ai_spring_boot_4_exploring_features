package com.example.springai.demo.feature15_gateway;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FEATURE 15 – Gate-Decider ("Parent Layer" ueber allen Feature-Controllern).
 *
 * <p>Ein einziger Eingang fuer beliebige natuerlichsprachliche Anfragen: Ein
 * KI-Router entscheidet, welches der bestehenden Features zustaendig ist, und
 * delegiert per Spring-AI-Tool-Calling an dessen Controller-Methode
 * ({@link GatewayTools}). Zurueck kommt die getroffene Route plus die roh
 * durchgereichte Antwort des gewaehlten Features – nicht die Neuformulierung des
 * Modells (siehe {@link RouteRecorder}).</p>
 *
 * <p>Passt fachlich kein Feature, wird die Anfrage <b>explizit abgelehnt</b>
 * (Route {@code none}), statt zu raten.</p>
 *
 * <p>Beispiel: {@code GET /api/gateway?question=Wie viele Versicherte haben E11.9?}</p>
 */
@RestController
public class GatewayController {

    private static final String SYSTEM_PROMPT = """
            Du bist ein Gate-Decider: ein KI-Router ueber den Feature-Endpunkten dieser Anwendung.
            Bestimme fuer die Nutzeranfrage das fachlich am besten passende Feature und rufe
            GENAU EIN dazu passendes Tool auf – niemals mehrere. Erfinde keine Antwort selbst;
            die Tools liefern die Inhalte.
            Wenn KEIN Tool fachlich passt, rufe gar kein Tool auf.

            Verfuegbare Routen (Tool -> Zweck):
              chat          -> allgemeine Konversation / freie Frage
              joke          -> einen Witz erzeugen
              tools         -> Produktkatalog / Lagerbestand
              rag           -> Wissensfragen zu Spring AI (RAG)
              embeddings    -> semantische Aehnlichkeit zweier Texte
              advisors      -> Frage mit aktivierten Chat-Advisors
              mcp           -> Frage unter Nutzung entfernter MCP-Tools
              observability -> Frage mit Observability-/GenAI-Metriken
              evaluate      -> Relevanz einer Antwort zu Frage/Kontext bewerten
              db            -> Aggregations-/Statistikfragen zum eGK-Datenbestand
            """;

    private final ChatClient chatClient;
    private final GatewayTools gatewayTools;
    private final RouteRecorder recorder;

    public GatewayController(ChatClient.Builder builder, GatewayTools gatewayTools,
                             RouteRecorder recorder) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
        this.gatewayTools = gatewayTools;
        this.recorder = recorder;
    }

    @GetMapping("/api/gateway")
    public GatewayResponse route(
            @RequestParam(defaultValue = "Wie viele Versicherte haben E11.9?") String question) {
        // Das Modell waehlt und ruft genau ein Gate-Tool; dessen Wahl landet im
        // request-scoped Recorder. Die freie Schlussantwort des Modells ignorieren wir.
        chatClient.prompt().user(question).tools(gatewayTools).call().content();

        if (recorder.isResolved()) {
            return new GatewayResponse(recorder.route(), recorder.antwort());
        }
        return new GatewayResponse("none", "Keine passende Funktion gefunden.");
    }

    /** Transparente Antwort des Gate-Deciders: gewaehlte Route + durchgereichte Antwort. */
    public record GatewayResponse(String route, String antwort) {
    }
}
