package com.example.springai.demo.feature14_dbquery;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReadOnlySqlTool {

    /** Maximale Laufzeit einer einzelnen Abfrage in Sekunden. */
    private static final int QUERY_TIMEOUT_SECONDS = 5;

    /** Mehr Zeilen werden nicht an das Modell zurueckgegeben. */
    private static final int MAX_ROWS = 100;

    private final JdbcTemplate jdbc;

    public ReadOnlySqlTool(DataSource dataSource) {
        // Eigener Template, damit der Query-Timeout nur fuer diesen (riskanteren)
        // Pfad gilt und nicht die global geteilte JdbcTemplate-Bean veraendert.
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        this.jdbc.setMaxRows(MAX_ROWS);
    }

    @Tool(description = "Fuehrt eine selbst formulierte, NUR LESENDE SQL-Abfrage (einzelnes SELECT) "
            + "gegen die eGK-Datenbank aus. Nur verwenden, wenn kein spezialisiertes Tool passt. "
            + "Keine schreibenden Befehle, kein Semikolon, ein Statement.")
    public String fuehreSelectAus(
            @ToolParam(description = "Ein einzelnes SELECT-Statement passend zum bekannten Schema")
            String sql) {
        String sicher;
        try {
            sicher = SqlGuard.sanitize(sql);
        } catch (IllegalArgumentException e) {
            // Klartext zurueck an das Modell, damit es seine Abfrage korrigieren kann.
            return "Abfrage abgelehnt: " + e.getMessage();
        }

        List<Map<String, Object>> rows = jdbc.queryForList(sicher);
        if (rows.isEmpty()) {
            return "Abfrage erfolgreich, keine Zeilen.";
        }
        return rows.stream()
                .map(row -> row.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining(", ")))
                .collect(Collectors.joining("\n"));
    }
}
