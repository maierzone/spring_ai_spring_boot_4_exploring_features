package com.example.springai.demo.feature19_moderation;

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
