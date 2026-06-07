package com.example.springai.demo.feature16_downloads;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * FEATURE 16 – Persistenz der heruntergeladenen Dateien in der Tabelle
 * {@code downloads} (siehe schema-downloads.sql). Die rohen Bytes liegen als
 * {@code bytea}; ausgeliefert werden sie spaeter ueber einen normalen
 * REST-Endpoint, nicht ueber das LLM.
 */
@Repository
public class DownloadRepository {

    private final JdbcTemplate jdbc;

    public DownloadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Listeneintrag fuer die "Bibliothek" im Frontend (ohne die Bytes). */
    public record DownloadInfo(long id, String sourceType, String title, String sourceRef,
                               String contentType, long sizeBytes) {
    }

    /** Die ausgelieferte Datei selbst (mit Bytes). */
    public record DownloadBytes(String contentType, String title, byte[] data) {
    }

    /** Speichert eine Datei und liefert die generierte Id zurueck. */
    public long save(String sourceType, String title, String sourceRef, String contentType, byte[] data) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO downloads (source_type, title, source_ref, content_type, size_bytes, data) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    new String[] {"id"});
            ps.setString(1, sourceType);
            ps.setString(2, title);
            ps.setString(3, sourceRef);
            ps.setString(4, contentType);
            ps.setLong(5, data.length);
            ps.setBytes(6, data);
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Kein generierter Schluessel fuer den Download erhalten.");
        }
        return key.longValue();
    }

    /** Alle gespeicherten Dateien, neueste zuerst (ohne Bytes). */
    public List<DownloadInfo> listAll() {
        return jdbc.query(
                "SELECT id, source_type, title, source_ref, content_type, size_bytes "
                        + "FROM downloads ORDER BY id DESC",
                (rs, n) -> new DownloadInfo(
                        rs.getLong("id"),
                        rs.getString("source_type"),
                        rs.getString("title"),
                        rs.getString("source_ref"),
                        rs.getString("content_type"),
                        rs.getLong("size_bytes")));
    }

    /** Eine Datei inkl. Bytes zur Auslieferung. */
    public Optional<DownloadBytes> load(long id) {
        try {
            DownloadBytes b = jdbc.queryForObject(
                    "SELECT content_type, title, data FROM downloads WHERE id = ?",
                    (rs, n) -> new DownloadBytes(
                            rs.getString("content_type"),
                            rs.getString("title"),
                            rs.getBytes("data")),
                    id);
            return Optional.ofNullable(b);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
