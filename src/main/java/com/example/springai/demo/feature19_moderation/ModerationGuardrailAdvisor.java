package com.example.springai.demo.feature19_moderation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * FEATURE 19 – Moderation als Guardrail-Advisor (Claude-as-Classifier).
 *
 * <p>Dieser <b>selbst geschriebene</b> Advisor klinkt sich – wie der
 * {@code MetricsLoggingAdvisor} aus Feature 10 – in die Aufrufkette ein und schuetzt
 * den Modellaufruf <em>in beide Richtungen</em>:</p>
 * <ol>
 *   <li><b>Input-Guardrail:</b> Die Nutzereingabe wird <em>vor</em> dem eigentlichen
 *       Modellaufruf geprueft. Bei einem Treffer wird die Kette gar nicht erst
 *       ausgefuehrt ({@code chain.nextCall} entfaellt) und eine Hinweis-Antwort
 *       zurueckgegeben.</li>
 *   <li><b>Output-Guardrail:</b> Die Modellantwort wird <em>nach</em> der Generierung
 *       geprueft und bei einem Treffer durch einen neutralen Hinweis ersetzt.</li>
 * </ol>
 *
 * <p>Gegenueber dem stichwortbasierten {@code SafeGuardAdvisor} (Feature 10) ist die
 * Pruefung hier <em>semantisch</em>: Sie delegiert an einen {@link ContentModerator},
 * der in der Anwendung von Claude selbst gestellt wird ({@link ClaudeContentModerator}).
 * In Tests wird stattdessen ein deterministisches Lambda eingesetzt – offline und
 * ohne API-Key.</p>
 */
public class ModerationGuardrailAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ModerationGuardrailAdvisor.class);

    /** Antworttext, wenn die Eingabe blockiert wird. */
    static final String BLOCKED_INPUT =
            "Deine Anfrage wurde wegen eines moeglichen Richtlinienverstosses abgelehnt.";

    /** Antworttext, wenn die Modellantwort gefiltert wird. */
    static final String BLOCKED_OUTPUT =
            "Die generierte Antwort wurde aus Sicherheitsgruenden gefiltert.";

    private final ContentModerator moderator;

    public ModerationGuardrailAdvisor(ContentModerator moderator) {
        this.moderator = moderator;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // 1) Eingabe pruefen – noch bevor das Modell ueberhaupt aufgerufen wird.
        ModerationVerdict inputVerdict = moderator.moderate(lastUserText(request));
        if (inputVerdict.flagged()) {
            log.info("[Moderation] Eingabe blockiert. Kategorien={}", inputVerdict.categories());
            return blocked(request, BLOCKED_INPUT, inputVerdict);
        }

        // 2) Restliche Kette (und am Ende das Modell) ausfuehren.
        ChatClientResponse response = chain.nextCall(request);

        // 3) Modellantwort pruefen und bei Treffer ersetzen.
        ModerationVerdict outputVerdict = moderator.moderate(assistantText(response));
        if (outputVerdict.flagged()) {
            log.info("[Moderation] Antwort gefiltert. Kategorien={}", outputVerdict.categories());
            return blocked(request, BLOCKED_OUTPUT, outputVerdict);
        }
        return response;
    }

    /** Baut eine kurzschliessende Antwort mit Hinweistext und legt das Urteil in den Kontext. */
    private ChatClientResponse blocked(ChatClientRequest request, String message, ModerationVerdict verdict) {
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(message))));
        Map<String, Object> context = new HashMap<>(request.context());
        context.put("moderation.flagged", true);
        context.put("moderation.categories", verdict.categories());
        return new ChatClientResponse(chatResponse, context);
    }

    /** Liefert den Text der letzten Nutzer-Nachricht (oder leer, falls keine vorhanden). */
    private static String lastUserText(ChatClientRequest request) {
        List<Message> messages = request.prompt().getInstructions();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                return userMessage.getText();
            }
        }
        return "";
    }

    /** Liest den Text der Modellantwort, falls vorhanden. */
    private static String assistantText(ChatClientResponse response) {
        if (response.chatResponse() == null || response.chatResponse().getResult() == null) {
            return "";
        }
        return response.chatResponse().getResult().getOutput().getText();
    }

    @Override
    public String getName() {
        return "ModerationGuardrailAdvisor";
    }

    /** Ganz aussen in der Kette, damit die Pruefung vor allen anderen Advisors greift. */
    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }
}
