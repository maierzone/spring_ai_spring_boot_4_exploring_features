package com.example.springai.demo.feature16_downloads;

import java.util.List;

import com.example.springai.demo.feature16_downloads.PaperSearchService.PaperHit;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * FEATURE 16 – Haelt die strukturierte Trefferliste pro HTTP-Anfrage fest.
 *
 * <p>Das {@link PaperTools}-Tool gibt dem Modell nur eine kurze Textzusammenfassung
 * zurueck. Fuer die anklickbare Liste im Frontend braucht der
 * {@link DownloadController} aber die rohen Treffer – genau die schreibt das Tool
 * hier hinein und der Controller liest sie nach dem LLM-Aufruf wieder aus
 * (gleiches Muster wie der RouteRecorder in Feature 15).</p>
 */
@Component
@RequestScope
public class PaperSearchRecorder {

    private String thema;
    private List<PaperHit> hits = List.of();

    public void record(String thema, List<PaperHit> hits) {
        this.thema = thema;
        this.hits = hits != null ? hits : List.of();
    }

    public String thema() {
        return thema;
    }

    public List<PaperHit> hits() {
        return hits;
    }
}
