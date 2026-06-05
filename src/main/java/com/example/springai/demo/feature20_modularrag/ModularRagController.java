package com.example.springai.demo.feature20_modularrag;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FEATURE 20 – Modulare / Advanced RAG ({@link RetrievalAugmentationAdvisor}).
 *
 * <p>Während Feature 7 mit dem {@code QuestionAnswerAdvisor} die <em>einfachste</em>
 * RAG-Variante zeigt (Frage → Ähnlichkeitssuche → Antwort), zerlegt der
 * {@link RetrievalAugmentationAdvisor} denselben Ablauf in eine <b>austauschbare
 * Pipeline</b>. Jeder Schritt ist eine eigene Komponente, die unabhängig
 * konfiguriert oder ersetzt werden kann:</p>
 *
 * <ol>
 *   <li><b>Pre-Retrieval – Query-Transformation:</b> Der
 *       {@link RewriteQueryTransformer} formt eine umgangssprachliche/mehrdeutige
 *       Nutzerfrage in eine fokussierte Suchanfrage um (entfernt Floskeln,
 *       schärft Schlüsselbegriffe) – das verbessert die Trefferqualität spürbar.</li>
 *   <li><b>Pre-Retrieval – Query-Expansion:</b> Der {@link MultiQueryExpander}
 *       erzeugt mehrere Formulierungsvarianten der Frage. So werden auch Dokumente
 *       gefunden, die dasselbe Konzept anders benennen (Synonyme, Umschreibungen).</li>
 *   <li><b>Retrieval:</b> Der {@link VectorStoreDocumentRetriever} sucht die
 *       passendsten Dokumente im {@link VectorStore} (gleicher Speicher wie
 *       Feature 7 – Telematik-/eGK-Wissen).</li>
 *   <li><b>Generation – Augmentation:</b> Der {@link ContextualQueryAugmenter}
 *       fügt die gefundenen Dokumente als Kontext in den finalen Prompt. Mit
 *       {@code allowEmptyContext(true)} antwortet das Modell auch dann sauber,
 *       wenn nichts Passendes gefunden wurde – statt einen Fehler zu werfen.</li>
 * </ol>
 *
 * <p>Beispiel: {@code GET /api/modular-rag?question=Kannst du mir kurz erklaeren,
 * was dieser GESPERRT-Status bei der eGK eigentlich bedeutet?}</p>
 *
 * <p><b>Kosten-Hinweis:</b> Transformation und Expansion sind je ein eigener
 * (kleiner) LLM-Aufruf <em>vor</em> dem eigentlichen Antwort-Aufruf. Das ist der
 * bewusste Trade-off der Advanced-RAG-Pipeline: mehr Aufrufe gegen bessere
 * Retrieval-Qualität.</p>
 */
@RestController
public class ModularRagController {

    private final ChatClient chatClient;
    private final VectorStoreDocumentRetriever documentRetriever;

    /**
     * <p>Der {@link ChatClient.Builder} ist prototype-scoped: Über den
     * {@link ObjectProvider} holen wir uns <b>zwei unabhängige</b> Builder-Instanzen.
     * Das ist hier wichtig, weil der äußere Builder den RAG-Advisor als
     * Default-Advisor erhält – würden die inneren Komponenten (Rewrite/Expand)
     * denselben Builder nutzen, liefen ihre LLM-Aufrufe erneut durch die
     * RAG-Pipeline (Rekursion).</p>
     */
    public ModularRagController(ObjectProvider<ChatClient.Builder> builders, VectorStore vectorStore) {
        // Eigener, "sauberer" Builder für die internen Pipeline-Schritte.
        ChatClient.Builder innerBuilder = builders.getObject();

        this.documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(3)
                .build();

        RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(RewriteQueryTransformer.builder()
                        .chatClientBuilder(innerBuilder)
                        .build())
                .queryExpander(MultiQueryExpander.builder()
                        .chatClientBuilder(innerBuilder)
                        .includeOriginal(true)
                        .numberOfQueries(3)
                        .build())
                .documentRetriever(documentRetriever)
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build())
                .build();

        this.chatClient = builders.getObject()
                .defaultAdvisors(ragAdvisor)
                .build();
    }

    @GetMapping("/api/modular-rag")
    public String ask(@RequestParam(defaultValue = "Was bedeutet der eGK-Status GESPERRT?") String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }

    /**
     * Transparenz-Endpunkt analog zu {@code /api/rag/sources} (Feature 7): zeigt,
     * welche Dokumente der {@link VectorStoreDocumentRetriever} – die
     * deterministische Mitte der Pipeline – für eine Frage liefert.
     *
     * <p>Bewusst <b>ohne</b> die LLM-Schritte (Rewrite/Expand): so funktioniert der
     * Endpunkt ohne API-Key und macht den reinen Retrieval-Schritt sichtbar.</p>
     */
    @GetMapping("/api/modular-rag/retrieve")
    public List<RetrievedSource> retrieve(
            @RequestParam(defaultValue = "Wozu dient der Heilberufsausweis?") String question) {

        List<Document> hits = documentRetriever.retrieve(new Query(question));

        return hits.stream()
                .map(doc -> new RetrievedSource(
                        doc.getMetadata().getOrDefault("source", "unknown").toString(),
                        doc.getScore(),
                        doc.getText()))
                .toList();
    }

    /** Ein abgerufenes Dokument inkl. Ähnlichkeits-Score und Quelle. */
    public record RetrievedSource(String source, Double score, String text) {
    }
}
