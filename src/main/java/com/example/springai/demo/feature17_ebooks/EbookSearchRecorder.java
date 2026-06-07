package com.example.springai.demo.feature17_ebooks;

import java.util.List;

import com.example.springai.demo.feature17_ebooks.EbookSearchService.EbookHit;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * FEATURE 17 – Haelt die strukturierte Trefferliste pro HTTP-Anfrage fest.
 *
 * <p>Das {@link EbookTools}-Tool gibt dem Modell nur eine kurze Textzusammenfassung
 * zurueck. Fuer die anklickbare Liste im Frontend braucht der
 * {@link EbookController} aber die rohen Treffer – genau die schreibt das Tool
 * hier hinein und der Controller liest sie nach dem LLM-Aufruf wieder aus
 * (gleiches Muster wie der PaperSearchRecorder in Feature 16).</p>
 */
@Component
@RequestScope
public class EbookSearchRecorder {

    private String titel;
    private List<EbookHit> hits = List.of();

    public void record(String titel, List<EbookHit> hits) {
        this.titel = titel;
        this.hits = hits != null ? hits : List.of();
    }

    public String titel() {
        return titel;
    }

    public List<EbookHit> hits() {
        return hits;
    }
}
