package com.example.springai.demo.feature16_downloads;

import java.util.List;

import com.example.springai.demo.feature16_downloads.PaperSearchService.PaperHit;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * FEATURE 16 – Tool Calling Expert.
 *
 * <p>Macht die OpenAlex-Recherche ({@link PaperSearchService}) dem Modell als
 * Werkzeug zugaenglich. Der Nutzer tippt frei ein Thema; das Modell erkennt die
 * Absicht und ruft {@code suchePaper} mit dem extrahierten Thema auf – der
 * "duenne Dispatcher". Das Tool liefert dem Modell eine kurze Zusammenfassung und
 * legt die strukturierten Treffer im {@link PaperSearchRecorder} ab, damit das
 * Frontend daraus eine anklickbare Auswahlliste bauen kann.</p>
 */
@Component
public class PaperTools {

    private final PaperSearchService search;
    private final PaperSearchRecorder recorder;

    public PaperTools(PaperSearchService search, PaperSearchRecorder recorder) {
        this.search = search;
        this.recorder = recorder;
    }

    @Tool(description = "Sucht ueber OpenAlex wissenschaftliche Paper zu einem Thema und liefert die "
            + "Top-5-Treffer mit Angabe, ob ein frei zugaenglicher Open-Access-Volltext existiert. "
            + "Nutze dieses Tool, wann immer der Nutzer Paper/Studien/Literatur zu einem Thema sucht.")
    public String suchePaper(
            @ToolParam(description = "Das Forschungs-/Suchthema, z.B. 'Neurodivergenz bei Tieren'") String thema) {
        List<PaperHit> hits = search.search(thema);
        recorder.record(thema, hits);
        if (hits.isEmpty()) {
            return "Keine Paper zu '" + thema + "' gefunden.";
        }
        StringBuilder sb = new StringBuilder("Top-").append(hits.size())
                .append(" Treffer zu '").append(thema).append("':\n");
        int i = 1;
        for (PaperHit h : hits) {
            sb.append(i++).append(". ").append(h.title())
                    .append(h.year() != null ? " (" + h.year() + ")" : "")
                    .append(h.authors() != null && !h.authors().isEmpty() ? " – " + h.authors() : "")
                    .append(h.openAccess() ? " [Open Access]" : " [kein freier Volltext]")
                    .append('\n');
        }
        sb.append("Der Nutzer kann die Open-Access-Treffer im UI auswaehlen und herunterladen.");
        return sb.toString();
    }
}
