package com.example.springai.demo.feature15_gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
 * Testet, dass jedes Gate-Tool an den richtigen Controller delegiert, das Ergebnis
 * unveraendert durchreicht und die Route im {@link RouteRecorder} protokolliert.
 * Die Feature-Controller sind gemockt – getestet wird allein die Routing-/Recorder-
 * Verdrahtung des Parent Layers, kein Modell.
 */
class GatewayToolsTest {

    private RouteRecorder recorder;
    private ChatClientController chat;
    private PromptTemplateController joke;
    private ToolCallingController tools;
    private RagController rag;
    private EmbeddingController embeddings;
    private AdvisorController advisors;
    private McpClientController mcp;
    private ObservabilityController observability;
    private EvaluationController evaluation;
    private DbQueryController db;
    private GatewayTools gateway;

    @BeforeEach
    void setUp() {
        recorder = new RouteRecorder();
        chat = mock(ChatClientController.class);
        joke = mock(PromptTemplateController.class);
        tools = mock(ToolCallingController.class);
        rag = mock(RagController.class);
        embeddings = mock(EmbeddingController.class);
        advisors = mock(AdvisorController.class);
        mcp = mock(McpClientController.class);
        observability = mock(ObservabilityController.class);
        evaluation = mock(EvaluationController.class);
        db = mock(DbQueryController.class);
        gateway = new GatewayTools(recorder, chat, joke, tools, rag, embeddings,
                advisors, mcp, observability, evaluation, db);
    }

    @Test
    void chatDelegiertUndProtokolliertRoute() {
        when(chat.chat("hallo")).thenReturn("Hi!");
        String result = gateway.chat("hallo");
        assertThat(result).isEqualTo("Hi!");
        assertThat(recorder.route()).isEqualTo("chat");
        assertThat(recorder.antwort()).isEqualTo("Hi!");
    }

    @Test
    void datenbankDelegiertAnDbController() {
        when(db.ask("wie viele?")).thenReturn("100");
        gateway.datenbank("wie viele?");
        assertThat(recorder.route()).isEqualTo("db");
        assertThat(recorder.antwort()).isEqualTo("100");
    }

    @Test
    void aehnlichkeitFormatiertEmbeddingErgebnis() {
        when(embeddings.similarity("a", "b"))
                .thenReturn(new SimilarityResult("a", "b", 0.875));
        String result = gateway.aehnlichkeit("a", "b");
        assertThat(recorder.route()).isEqualTo("embeddings");
        assertThat(result).contains("0,875").contains("'a'").contains("'b'");
    }

    @Test
    void bewertungFormatiertEvaluationErgebnis() {
        when(evaluation.evaluateRelevancy("f", "k", "a"))
                .thenReturn(new EvaluationResult(true, "passt"));
        String result = gateway.bewertung("f", "k", "a");
        assertThat(recorder.route()).isEqualTo("evaluate");
        assertThat(result).contains("relevant").contains("passt");
    }

    @Test
    void witzReichtThemaUndSpracheWeiter() {
        when(joke.joke("Kaffee", "Deutsch")).thenReturn("Ein Witz.");
        gateway.witz("Kaffee", "Deutsch");
        assertThat(recorder.route()).isEqualTo("joke");
        assertThat(recorder.antwort()).isEqualTo("Ein Witz.");
    }
}
