package com.example.springai.demo.feature15_gateway;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.springai.demo.feature01_chatclient.ChatClientController;
import com.example.springai.demo.feature03_prompttemplate.PromptTemplateController;
import com.example.springai.demo.feature06_tools.ToolCallingController;
import com.example.springai.demo.feature07_rag.RagController;
import com.example.springai.demo.feature08_embeddings.EmbeddingController;
import com.example.springai.demo.feature08_embeddings.EmbeddingController.SimilarityResult;
import com.example.springai.demo.feature10_advisors.AdvisorController;
import com.example.springai.demo.feature11_mcp.McpClientController;
import com.example.springai.demo.feature12_observability.ObservabilityController;
import com.example.springai.demo.feature13_evaluation.EvaluationController;
import com.example.springai.demo.feature13_evaluation.EvaluationController.EvaluationResult;
import com.example.springai.demo.feature14_dbquery.DbQueryController;

/**
 * FEATURE 15 – Werkzeugkasten des Gate-Deciders ("Parent Layer").
 *
 * <p>Jedes natuerlichsprachliche GET-Feature der Anwendung ist hier als ein
 * {@link Tool} hinterlegt, das an die <b>bestehende</b> Controller-Bean delegiert
 * (DRY – keine duplizierte Logik). Das Gate-LLM ({@link GatewayController}) waehlt
 * anhand der Anfrage genau eines dieser Tools; die eigentliche Arbeit erledigt
 * weiterhin der jeweilige Feature-Controller.</p>
 *
 * <p>Jedes Tool meldet seine Wahl ueber den {@link RouteRecorder}, damit der
 * Controller die getroffene Route und die roh durchgereichte Antwort zurueckgeben
 * kann – statt der freien Schlussformulierung des Modells.</p>
 */
@Component
public class GatewayTools {

    private final RouteRecorder recorder;
    private final ChatClientController chat;
    private final PromptTemplateController joke;
    private final ToolCallingController tools;
    private final RagController rag;
    private final EmbeddingController embeddings;
    private final AdvisorController advisors;
    private final McpClientController mcp;
    private final ObservabilityController observability;
    private final EvaluationController evaluation;
    private final DbQueryController db;

    // Aggregations-Punkt des Parent Layers: bewusst breite Konstruktor-Injektion
    // aller delegierten Feature-Controller plus des request-scoped Recorders.
    public GatewayTools(RouteRecorder recorder,
                        ChatClientController chat,
                        PromptTemplateController joke,
                        ToolCallingController tools,
                        RagController rag,
                        EmbeddingController embeddings,
                        AdvisorController advisors,
                        McpClientController mcp,
                        ObservabilityController observability,
                        EvaluationController evaluation,
                        DbQueryController db) {
        this.recorder = recorder;
        this.chat = chat;
        this.joke = joke;
        this.tools = tools;
        this.rag = rag;
        this.embeddings = embeddings;
        this.advisors = advisors;
        this.mcp = mcp;
        this.observability = observability;
        this.evaluation = evaluation;
        this.db = db;
    }

    @Tool(description = "Allgemeine Konversation oder freie Frage ohne speziellen Feature-Bezug "
            + "(Smalltalk, Begruessung, einfache Auskunft).")
    public String chat(@ToolParam(description = "Die Nutzerfrage im Originalwortlaut") String nachricht) {
        return route("chat", chat.chat(nachricht));
    }

    @Tool(description = "Erzeugt einen Witz zu einem Thema in einer Sprache.")
    public String witz(
            @ToolParam(description = "Thema des Witzes, z.B. 'Programmierer'") String thema,
            @ToolParam(description = "Sprache, z.B. 'Deutsch'") String sprache) {
        return route("joke", joke.joke(thema, sprache));
    }

    @Tool(description = "Fuehrt eGK-Einzelsatz-Pruefungen per Tool-Calling aus: KVNR-/Luhn-Pruefziffer "
            + "validieren, eGK-Kartenstatus, ICD-10-Code aufloesen, Zertifikats-Gueltigkeit.")
    public String egkPruefung(
            @ToolParam(description = "Frage zu einer konkreten Nummer/Karte/Diagnose/Zertifikat") String nachricht) {
        return route("tools", tools.ask(nachricht));
    }

    @Tool(description = "Beantwortet Wissensfragen zu Telematik-/eGK-Fachbegriffen aus dem "
            + "Wissensspeicher (RAG).")
    public String wissensspeicher(@ToolParam(description = "Wissensfrage") String frage) {
        return route("rag", rag.ask(frage));
    }

    @Tool(description = "Berechnet die semantische Aehnlichkeit (Kosinus) zwischen zwei Texten.")
    public String aehnlichkeit(
            @ToolParam(description = "Erster Text") String text1,
            @ToolParam(description = "Zweiter Text") String text2) {
        SimilarityResult r = embeddings.similarity(text1, text2);
        return route("embeddings", "Kosinus-Aehnlichkeit zwischen '" + r.text1() + "' und '"
                + r.text2() + "': " + String.format("%.3f", r.cosineSimilarity()));
    }

    @Tool(description = "Beantwortet eine Frage mit aktivierten Chat-Advisors (Logging/Metriken).")
    public String mitAdvisors(@ToolParam(description = "Die Frage") String nachricht) {
        return route("advisors", advisors.ask(nachricht).answer());
    }

    @Tool(description = "Beantwortet eine Frage unter Nutzung entfernter MCP-Server-Tools.")
    public String mcpClient(@ToolParam(description = "Die Frage") String nachricht) {
        return route("mcp", mcp.askWithRemoteTools(nachricht));
    }

    @Tool(description = "Beantwortet eine Frage und erzeugt dabei Observability-/GenAI-Metriken.")
    public String mitObservability(@ToolParam(description = "Die Frage") String nachricht) {
        return route("observability", observability.ask(nachricht));
    }

    @Tool(description = "Bewertet, ob eine Antwort relevant zur Frage und zum Kontext ist (Evaluation).")
    public String bewertung(
            @ToolParam(description = "Die urspruengliche Frage") String frage,
            @ToolParam(description = "Der zugrunde liegende Kontext") String kontext,
            @ToolParam(description = "Die zu bewertende Antwort") String antwort) {
        EvaluationResult r = evaluation.evaluateRelevancy(frage, kontext, antwort);
        return route("evaluate", "Bewertung: " + (r.pass() ? "relevant" : "nicht relevant")
                + " – " + r.feedback());
    }

    @Tool(description = "Beantwortet Aggregations-/Statistikfragen zum eGK-Datenbestand "
            + "(Versicherte, Diagnosen, Karten, Zertifikate, Krankenkassen, Leistungserbringer).")
    public String datenbank(@ToolParam(description = "Die Datenbank-Frage") String frage) {
        return route("db", db.ask(frage));
    }

    /** Protokolliert die Wahl im Recorder und liefert das Ergebnis ans Modell zurueck. */
    private String route(String route, String antwort) {
        recorder.record(route, antwort);
        return antwort;
    }
}
