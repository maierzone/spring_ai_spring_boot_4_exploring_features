package com.example.springai.demo.feature19_moderation;

/**
 * Schmale Abstraktion fuer eine Inhaltspruefung: Text rein, {@link ModerationVerdict} raus.
 *
 * <p>Bewusst als funktionale Schnittstelle gehalten – wie {@code Generator}/{@code Evaluator}
 * in Feature 16. Dadurch laesst sich der {@link ModerationGuardrailAdvisor} vollstaendig
 * offline und deterministisch testen (Lambda statt LLM), waehrend in der Anwendung der
 * {@link ClaudeContentModerator} den echten Modellaufruf uebernimmt.</p>
 *
 * <p>Hintergrund: Anthropic bietet – anders als OpenAI/Mistral – keinen dedizierten
 * Moderation-Endpunkt, also kann Spring AI hier kein {@code ModerationModel}
 * autokonfigurieren. Der von Anthropic empfohlene Weg ist, <b>Claude selbst als
 * Klassifikator</b> ueber die normale Chat-API zu nutzen – genau das tut die
 * Produktivimplementierung, ohne einen zweiten Provider oder API-Key.</p>
 */
@FunctionalInterface
public interface ContentModerator {

    /**
     * Prueft den uebergebenen Text und liefert ein typisiertes Urteil.
     *
     * @param text der zu pruefende Inhalt (Nutzereingabe oder Modellantwort)
     * @return das Urteil; nie {@code null}
     */
    ModerationVerdict moderate(String text);
}
