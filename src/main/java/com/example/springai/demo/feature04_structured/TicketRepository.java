package com.example.springai.demo.feature04_structured;

import java.util.List;

import com.example.springai.demo.feature04_structured.StructuredOutputController.Category;
import com.example.springai.demo.feature04_structured.StructuredOutputController.TicketAnalysis;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * FEATURE E – Persistenz & Analytics fuer die Ticket-Analyse.
 *
 * <p>Schliesst den Kreis des Structured-Output-Features: Die vom LLM erzeugte,
 * typisierte {@link TicketAnalysis} wird hier in einer echten PostgreSQL-Tabelle
 * abgelegt und laesst sich anschliessend per SQL auswerten. So wird aus
 * "KI-Extraktion" eine ganz normale, abfragbare Geschaeftsdatenbasis.</p>
 *
 * <p>Verwendet {@link JdbcClient} – die moderne, schlanke JDBC-API aus Spring 6
 * (in Spring Boot 4 enthalten). Bewusst ohne ORM, damit das tatsaechliche SQL
 * sichtbar bleibt.</p>
 */
@Repository
public class TicketRepository {

    private final JdbcClient jdbc;

    public TicketRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Speichert eine Analyse samt urspruenglichem Ticket-Text. */
    public void save(TicketAnalysis analysis, String originalText) {
        jdbc.sql("""
                INSERT INTO tickets (category, priority, sentiment, summary, original_text)
                VALUES (?, ?, ?, ?, ?)
                """)
                .params(analysis.category().name(),
                        analysis.priority().name(),
                        analysis.customerSentiment().name(),
                        analysis.summary(),
                        originalText)
                .update();
    }

    /**
     * Aggregierte Auswertung: wie viele Tickets pro Kategorie? Klassisches
     * {@code GROUP BY} – die Frucht der Persistenz.
     */
    public List<CategoryCount> countByCategory() {
        return jdbc.sql("""
                SELECT category, COUNT(*) AS cnt
                FROM tickets
                GROUP BY category
                ORDER BY cnt DESC
                """)
                .query((rs, rowNum) ->
                        new CategoryCount(Category.valueOf(rs.getString("category")), rs.getLong("cnt")))
                .list();
    }

    /** Ergebniszeile der Kategorie-Statistik. */
    public record CategoryCount(Category category, long count) {
    }
}
