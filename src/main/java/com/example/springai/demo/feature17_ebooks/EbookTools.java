package com.example.springai.demo.feature17_ebooks;

import java.util.List;

import com.example.springai.demo.feature17_ebooks.EbookSearchService.EbookHit;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * FEATURE 17 – eBook-Download-Tool.
 *
 * <p>Macht die gemeinfreie eBook-Recherche ({@link EbookSearchService}) dem Modell
 * als Werkzeug zugaenglich. Der Nutzer tippt frei einen Titel/Autor; das Modell
 * erkennt die Absicht und ruft {@code sucheEbook} mit dem extrahierten Suchbegriff
 * auf – der "duenne Dispatcher". Das Tool liefert dem Modell eine kurze
 * Zusammenfassung und legt die strukturierten Treffer im {@link EbookSearchRecorder}
 * ab, damit das Frontend daraus eine anklickbare Auswahlliste bauen kann.</p>
 */
@Component
public class EbookTools {

    private final EbookSearchService search;
    private final EbookSearchRecorder recorder;

    public EbookTools(EbookSearchService search, EbookSearchRecorder recorder) {
        this.search = search;
        this.recorder = recorder;
    }

    @Tool(description = "Sucht gemeinfreie (urheberrechtsfreie) eBooks ueber Project Gutenberg und Open "
            + "Library und liefert Treffer mit Angabe, ob ein frei herunterladbarer Volltext existiert. "
            + "Nutze dieses Tool, wann immer der Nutzer ein Buch zum Lesen/Herunterladen sucht.")
    public String sucheEbook(
            @ToolParam(description = "Buchtitel und/oder Autor, z.B. 'Faust Goethe' oder 'Sherlock Holmes'") String titel) {
        List<EbookHit> hits = search.search(titel);
        recorder.record(titel, hits);
        if (hits.isEmpty()) {
            return "Keine gemeinfreien eBooks zu '" + titel + "' gefunden.";
        }
        StringBuilder sb = new StringBuilder("Treffer zu '").append(titel).append("':\n");
        int i = 1;
        for (EbookHit h : hits) {
            sb.append(i++).append(". ").append(h.title())
                    .append(h.year() != null ? " (" + h.year() + ")" : "")
                    .append(h.authors() != null && !h.authors().isEmpty() ? " – " + h.authors() : "")
                    .append(h.downloadable() ? " [" + h.format() + "]" : " [kein freier Download]")
                    .append('\n');
        }
        sb.append("Der Nutzer kann die herunterladbaren Treffer im UI auswaehlen und speichern.");
        return sb.toString();
    }
}
