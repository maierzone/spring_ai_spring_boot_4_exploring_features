package com.example.springai.demo.feature21_exceptiontriage;

import java.util.List;

import com.example.springai.demo.feature21_exceptiontriage.ExceptionTriage.Category;
import com.example.springai.demo.feature21_exceptiontriage.ExceptionTriage.Severity;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Persistenz &amp; Analytics der KI-Triage – analog zum {@code TicketRepository}
 * aus Feature 4. Jede abgeschlossene Analyse landet in einer echten Tabelle und
 * wird per {@code GROUP BY} auswertbar ("Fehler nach Kategorie / Severity").
 *
 * <p>Nutzt {@link JdbcClient} (Spring 6) bewusst ohne ORM, damit das SQL sichtbar
 * bleibt. Das Schema ({@code schema-triage.sql}) ist ANSI-nah und laeuft auf
 * PostgreSQL (App) wie H2 (Tests).</p>
 */
@Repository
public class ExceptionTriageRepository {

    private final JdbcClient jdbc;

    public ExceptionTriageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Speichert das Ergebnis einer Triage samt Herkunftskontext. */
    public void save(ExceptionContext ctx, ExceptionTriage triage) {
        jdbc.sql("""
                INSERT INTO exception_triage
                    (fingerprint, error_id, exception_type, path, category, severity,
                     retriable, root_cause, suggested_action, user_message)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .params(ctx.fingerprint(),
                        ctx.errorId(),
                        ctx.exceptionType(),
                        ctx.path(),
                        triage.category().name(),
                        triage.severity().name(),
                        triage.retriable(),
                        triage.probableRootCause(),
                        triage.suggestedAction(),
                        triage.userFacingMessage())
                .update();
    }

    /** Aggregierte Auswertung: Anzahl Vorfaelle pro Kategorie. */
    public List<CategoryCount> countByCategory() {
        return jdbc.sql("""
                SELECT category, COUNT(*) AS cnt
                FROM exception_triage
                GROUP BY category
                ORDER BY cnt DESC
                """)
                .query((rs, rowNum) ->
                        new CategoryCount(Category.valueOf(rs.getString("category")), rs.getLong("cnt")))
                .list();
    }

    /** Aggregierte Auswertung: Anzahl Vorfaelle pro Severity. */
    public List<SeverityCount> countBySeverity() {
        return jdbc.sql("""
                SELECT severity, COUNT(*) AS cnt
                FROM exception_triage
                GROUP BY severity
                ORDER BY cnt DESC
                """)
                .query((rs, rowNum) ->
                        new SeverityCount(Severity.valueOf(rs.getString("severity")), rs.getLong("cnt")))
                .list();
    }

    public record CategoryCount(Category category, long count) {
    }

    public record SeverityCount(Severity severity, long count) {
    }
}
