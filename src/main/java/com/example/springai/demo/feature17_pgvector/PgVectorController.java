package com.example.springai.demo.feature17_pgvector;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FEATURE 17 – pgvector als persistenter VectorStore.
 *
 * <p>Während Feature 7 (RAG) und Feature 8 (Embeddings) den Vektorspeicher nur
 * als Black Box nutzen, macht dieses Feature den Wechsel vom flüchtigen
 * {@code SimpleVectorStore} auf einen <b>persistenten</b> {@code PgVectorStore}
 * sichtbar. Beide implementieren dasselbe {@link VectorStore}-Interface – der
 * Anwendungscode hier bleibt identisch, getauscht wird allein die Bean (siehe
 * {@code DemoBeans} und das Profil {@code pgvector}).</p>
 *
 * <p>Die beiden Stärken, die pgvector gegenüber dem In-Memory-Speicher ausspielt,
 * werden hier vorgeführt:</p>
 * <ul>
 *   <li><b>Persistenz:</b> Über {@code POST /api/pgvector/documents} abgelegte
 *       Dokumente landen in der Postgres-Tabelle {@code vector_store} und
 *       <em>überleben einen Neustart</em> der Anwendung.</li>
 *   <li><b>Metadaten-Filter:</b> Die Ähnlichkeitssuche lässt sich mit einem
 *       Filter-Ausdruck auf Metadaten kombinieren (hier: {@code category}). Das
 *       Filtern übernimmt bei pgvector die Datenbank.</li>
 * </ul>
 *
 * <p>Hinweis: In den Tests ({@code @ActiveProfiles("test")}) ist statt pgvector
 * der {@code SimpleVectorStore} aktiv. Auch er beherrscht Metadaten-Filter, sodass
 * die Logik dieses Controllers offline testbar bleibt.</p>
 */
@RestController
public class PgVectorController {

    private final VectorStore vectorStore;

    public PgVectorController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Zeigt, welche {@link VectorStore}-Implementierung zur Laufzeit aktiv ist.
     * In der Standardlaufzeit ist das {@code PgVectorStore}, in den Tests
     * {@code SimpleVectorStore}. Praktisch zum Verifizieren des Profil-Switch.
     */
    @GetMapping("/api/pgvector/info")
    public Map<String, String> info() {
        return Map.of("vectorStore", vectorStore.getClass().getSimpleName());
    }

    /**
     * Legt ein Dokument mit einem {@code category}-Metadatum im Vektorspeicher ab.
     * Bei aktivem pgvector wird der Eintrag dauerhaft in PostgreSQL gespeichert.
     *
     * <p>Beispiel:
     * {@code POST /api/pgvector/documents?text=Spring AI vereinfacht RAG&category=spring}</p>
     */
    @PostMapping("/api/pgvector/documents")
    public Map<String, String> add(@RequestParam String text,
                                   @RequestParam(defaultValue = "general") String category) {
        Document document = Document.builder()
                .text(text)
                .metadata("category", category)
                .build();
        vectorStore.add(List.of(document));
        return Map.of("id", document.getId(), "category", category);
    }

    /**
     * Ähnlichkeitssuche, optional eingeschränkt auf eine Kategorie. Wird
     * {@code category} angegeben, fließt ein Metadaten-Filter in die Anfrage ein –
     * pgvector wertet ihn direkt in der Datenbank aus.
     *
     * <p>Beispiel:
     * {@code GET /api/pgvector/search?query=Was ist RAG?&category=spring&topK=3}</p>
     */
    @GetMapping("/api/pgvector/search")
    public List<Hit> search(@RequestParam String query,
                            @RequestParam(required = false) String category,
                            @RequestParam(defaultValue = "3") int topK) {

        SearchRequest.Builder request = SearchRequest.builder().query(query).topK(topK);
        if (category != null && !category.isBlank()) {
            // Filter-Ausdruck in der portablen Spring-AI-Syntax. Der jeweilige
            // VectorStore uebersetzt ihn in sein natives Format (bei pgvector SQL).
            request.filterExpression("category == '" + category + "'");
        }

        List<Document> hits = vectorStore.similaritySearch(request.build());
        return hits.stream()
                .map(doc -> new Hit(
                        doc.getMetadata().getOrDefault("category", "unknown").toString(),
                        doc.getScore(),
                        doc.getText()))
                .toList();
    }

    /** Ein Treffer der Ähnlichkeitssuche inkl. Kategorie und Score. */
    public record Hit(String category, Double score, String text) {
    }
}
