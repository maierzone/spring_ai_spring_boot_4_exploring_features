package com.example.springai.demo.feature14_dbquery;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * FEATURE 14 – generischer, rein lesender SQL-Execute-Endpoint.
 */
@RestController
public class SqlExecuteController {

    private static final int QUERY_TIMEOUT_SECONDS = 5;

    private static final int MAX_ROWS = 100;

    private final JdbcTemplate jdbc;

    public SqlExecuteController(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        this.jdbc.setMaxRows(MAX_ROWS);
    }

    public record SqlResult(String sql, List<String> columns, List<List<Object>> rows) {
    }

    @PostMapping(value = "/api/db/execute", consumes = MediaType.TEXT_PLAIN_VALUE)
    public SqlResult execute(@RequestBody String sql) {
        String safe;
        try {
            safe = SqlGuard.sanitize(sql);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return jdbc.query(safe, rs -> {
            var meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            List<String> columns = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                columns.add(meta.getColumnLabel(i));
            }
            List<List<Object>> rows = new ArrayList<>();
            while (rs.next()) {
                List<Object> row = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    row.add(rs.getObject(i));
                }
                rows.add(row);
            }
            return new SqlResult(safe, columns, rows);
        });
    }
}
