package com.example.springai.demo.feature19_moderation;

import java.util.List;


public record ModerationVerdict(boolean flagged, List<String> categories, String reason) {

    /** Unbedenkliches Urteil – nichts markiert. */
    public static ModerationVerdict safe() {
        return new ModerationVerdict(false, List.of(), "");
    }
}
