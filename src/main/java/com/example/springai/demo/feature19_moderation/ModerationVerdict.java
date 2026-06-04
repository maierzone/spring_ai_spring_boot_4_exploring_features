package com.example.springai.demo.feature19_moderation;

import java.util.List;

/**
 * Typisiertes Ergebnis einer Inhaltspruefung (siehe {@link ContentModerator}).
 *
 * <p>Bewusst nah an der Spring-AI-{@code ModerationModel}-Abstraktion (OpenAI/Mistral)
 * modelliert, obwohl hier <b>Claude selbst als Klassifikator</b> arbeitet: ein
 * {@code flagged}-Schalter plus die ausloesenden Kategorien und eine kurze
 * Begruendung. So bleibt das Ergebnis providerunabhaengig auswertbar.</p>
 *
 * @param flagged    {@code true}, wenn der Text gegen mindestens eine Richtlinie verstoesst
 * @param categories ausloesende Kategorien (z.&nbsp;B. {@code hate}, {@code violence}); leer wenn unbedenklich
 * @param reason     knappe Begruendung des Urteils (fuer Logging/Transparenz)
 */
public record ModerationVerdict(boolean flagged, List<String> categories, String reason) {

    /** Unbedenkliches Urteil – nichts markiert. */
    public static ModerationVerdict safe() {
        return new ModerationVerdict(false, List.of(), "");
    }
}
