package com.example.springai.demo.feature15_gateway;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
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
              tools         -> eGK-Einzelsatz-Pruefungen (KVNR-/Luhn-Pruefziffer, Kartenstatus,
                               ICD-10 aufloesen, Zertifikats-Gueltigkeit)
              rag           -> Wissensfragen zu Telematik-/eGK-Fachbegriffen (RAG)
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
    private final CallFlowRecorder callFlow;

    public GatewayController(ChatClient.Builder builder, GatewayTools gatewayTools,
                             RouteRecorder recorder, CallFlowRecorder callFlow) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
        this.gatewayTools = gatewayTools;
        this.recorder = recorder;
        this.callFlow = callFlow;
    }

    @GetMapping("/api/gateway")
    public GatewayResponse route(
            @RequestParam(defaultValue = "Wie viele Versicherte haben E11.9?") String question) {
        // Ab hier den echten Methoden-Aufruf-Flow mitschneiden (siehe CallFlowAspect),
        // mit dieser Endpunkt-Methode als Wurzel des Aufruf-Baums.
        callFlow.start("GatewayController.route(String)", "GET /api/gateway");
        // Wall-Clock-Messung der gesamten Routing-Verarbeitung (Modellaufruf inkl.
        // der synchron im Tool ausgefuehrten Feature-Delegation).
        long startNanos = System.nanoTime();
        ChatResponse chatResponse = null;
        List<CallNode> flow;
        try {
            // Das Modell waehlt und ruft genau ein Gate-Tool; dessen Wahl landet im
            // request-scoped Recorder. Die freie Schlussantwort des Modells ignorieren wir,
            // lesen aus der ChatResponse aber den Token-Verbrauch dieser Anfrage.
            chatResponse = chatClient.prompt().user(question).tools(gatewayTools).call().chatResponse();
        } finally {
            // Immer einsammeln (auch bei Fehler) – raeumt zugleich den Thread-Local auf.
            flow = callFlow.stopAndCollect();
        }
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
        RouteMetrics metrics = RouteMetrics.from(usageOf(chatResponse), latencyMs);

        if (recorder.isResolved()) {
            return new GatewayResponse(recorder.route(), recorder.antwort(), flow, metrics);
        }
        return new GatewayResponse("none", "Keine passende Funktion gefunden.", flow, metrics);
    }

    /** Token-Verbrauch aus den Antwort-Metadaten lesen, sofern vorhanden. */
    private static Usage usageOf(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return null;
        }
        return chatResponse.getMetadata().getUsage();
    }

    /**
     * Transparente Antwort des Gate-Deciders: gewaehlte Route, durchgereichte Antwort,
     * der mitgeschnittene Methoden-Aufruf-Flow ({@link CallNode}-Baum) und die
     * aufgewendeten Ressourcen dieser Anfrage ({@link RouteMetrics}).
     */
    public record GatewayResponse(String route, String antwort, List<CallNode> flow, RouteMetrics metrics) {
    }

    /**
     * Ressourcenverbrauch einer Gateway-Anfrage fuer die Badge-Anzeige: Token-Zahlen
     * (dieselben Werte, die Spring AIs Observability als {@code gen_ai.client.token.usage}
     * exportiert) und die Wall-Clock-Verarbeitungszeit. Token-Felder sind {@code null},
     * falls das Modell keine Usage liefert (z.&nbsp;B. ohne API-Key).
     */
    public record RouteMetrics(Integer inputTokens, Integer outputTokens, Integer totalTokens, long latencyMs) {

        static RouteMetrics from(Usage usage, long latencyMs) {
            if (usage == null) {
                return new RouteMetrics(null, null, null, latencyMs);
            }
            return new RouteMetrics(
                    usage.getPromptTokens(),
                    usage.getCompletionTokens(),
                    usage.getTotalTokens(),
                    latencyMs);
        }
    }
}
