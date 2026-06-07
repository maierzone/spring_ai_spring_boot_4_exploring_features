package com.example.springai.demo.testdata;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.example.springai.demo.testdata.TestDataGenerator.CaZertifikat;
import com.example.springai.demo.testdata.TestDataGenerator.Dataset;
import com.example.springai.demo.testdata.TestDataGenerator.Diagnose;
import com.example.springai.demo.testdata.TestDataGenerator.EgkKarte;
import com.example.springai.demo.testdata.TestDataGenerator.Krankenkasse;
import com.example.springai.demo.testdata.TestDataGenerator.Leistungserbringer;
import com.example.springai.demo.testdata.TestDataGenerator.Versicherter;
import com.example.springai.demo.testdata.TestDataGenerator.Zertifikat;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EgkTestDataRepository {

    private static final List<String> TABELLEN_LOESCH_REIHENFOLGE = List.of(
            "zertifikate", "diagnosen", "egk_karten", "versicherte",
            "leistungserbringer", "ca_zertifikate", "krankenkassen");

    private final JdbcTemplate jdbc;

    public EgkTestDataRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void replaceAll(Dataset d) {
        clear();
        insertKrankenkassen(d.krankenkassen());
        insertCas(d.caZertifikate());
        insertLeistungserbringer(d.leistungserbringer());
        insertVersicherte(d.versicherte());
        insertKarten(d.karten());
        insertDiagnosen(d.diagnosen());
        insertZertifikate(d.zertifikate());
    }

    @Transactional
    public void clear() {
        TABELLEN_LOESCH_REIHENFOLGE.forEach(t -> jdbc.update("DELETE FROM " + t));
    }

    // --- Inserts ------------------------------------------------------------
    private void insertKrankenkassen(List<Krankenkasse> list) {
        jdbc.batchUpdate("INSERT INTO krankenkassen (id, ik, name, typ) VALUES (?, ?, ?, ?)",
                setter(list, (ps, k) -> {
                    ps.setLong(1, k.id());
                    ps.setString(2, k.ik());
                    ps.setString(3, k.name());
                    ps.setString(4, k.typ());
                }));
    }

    private void insertCas(List<CaZertifikat> list) {
        jdbc.batchUpdate("""
                INSERT INTO ca_zertifikate (id, name, subject_dn, seriennummer, not_before, not_after)
                VALUES (?, ?, ?, ?, ?, ?)
                """, setter(list, (ps, c) -> {
            ps.setLong(1, c.id());
            ps.setString(2, c.name());
            ps.setString(3, c.subjectDn());
            ps.setString(4, c.seriennummer());
            ps.setObject(5, c.notBefore());
            ps.setObject(6, c.notAfter());
        }));
    }

    private void insertLeistungserbringer(List<Leistungserbringer> list) {
        jdbc.batchUpdate("""
                INSERT INTO leistungserbringer (id, typ, name, lanr, bsnr, plz, ort)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, setter(list, (ps, l) -> {
            ps.setLong(1, l.id());
            ps.setString(2, l.typ());
            ps.setString(3, l.name());
            ps.setString(4, l.lanr());
            ps.setString(5, l.bsnr());
            ps.setString(6, l.plz());
            ps.setString(7, l.ort());
        }));
    }

    private void insertVersicherte(List<Versicherter> list) {
        jdbc.batchUpdate("""
                INSERT INTO versicherte (id, kvnr, vorname, nachname, geburtsdatum, geschlecht,
                                         plz, ort, strasse, krankenkasse_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, setter(list, (ps, v) -> {
            ps.setLong(1, v.id());
            ps.setString(2, v.kvnr());
            ps.setString(3, v.vorname());
            ps.setString(4, v.nachname());
            ps.setObject(5, v.geburtsdatum());
            ps.setString(6, v.geschlecht());
            ps.setString(7, v.plz());
            ps.setString(8, v.ort());
            ps.setString(9, v.strasse());
            ps.setLong(10, v.krankenkasseId());
        }));
    }

    private void insertKarten(List<EgkKarte> list) {
        jdbc.batchUpdate("""
                INSERT INTO egk_karten (id, iccsn, can, versicherte_id, gueltig_von, gueltig_bis, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, setter(list, (ps, k) -> {
            ps.setLong(1, k.id());
            ps.setString(2, k.iccsn());
            ps.setString(3, k.can());
            ps.setLong(4, k.versicherteId());
            ps.setObject(5, k.gueltigVon());
            ps.setObject(6, k.gueltigBis());
            ps.setString(7, k.status());
        }));
    }

    private void insertDiagnosen(List<Diagnose> list) {
        jdbc.batchUpdate("""
                INSERT INTO diagnosen (id, versicherte_id, leistungserbringer_id, icd10,
                                       diagnose_text, diagnose_datum)
                VALUES (?, ?, ?, ?, ?, ?)
                """, setter(list, (ps, dg) -> {
            ps.setLong(1, dg.id());
            ps.setLong(2, dg.versicherteId());
            ps.setLong(3, dg.leistungserbringerId());
            ps.setString(4, dg.icd10());
            ps.setString(5, dg.diagnoseText());
            ps.setObject(6, dg.datum());
        }));
    }

    private void insertZertifikate(List<Zertifikat> list) {
        jdbc.batchUpdate("""
                INSERT INTO zertifikate (id, typ, seriennummer, subject_dn, ca_id, inhaber_typ,
                                         inhaber_id, not_before, not_after, status, revocation_datum)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, setter(list, (ps, z) -> {
            ps.setLong(1, z.id());
            ps.setString(2, z.typ());
            ps.setString(3, z.seriennummer());
            ps.setString(4, z.subjectDn());
            ps.setLong(5, z.caId());
            ps.setString(6, z.inhaberTyp());
            ps.setLong(7, z.inhaberId());
            ps.setObject(8, z.notBefore());
            ps.setObject(9, z.notAfter());
            ps.setString(10, z.status());
            ps.setObject(11, z.revocationDatum()); // darf NULL sein
        }));
    }

    // --- Auswertung ---------------------------------------------------------

    /** Zaehlt die Zeilen je Tabelle plus die Verteilung der Zertifikatsstatus. */
    public Stats stats() {
        return new Stats(
                count("krankenkassen"),
                count("leistungserbringer"),
                count("versicherte"),
                count("egk_karten"),
                count("diagnosen"),
                count("zertifikate"),
                gruppiere("SELECT status, COUNT(*) FROM zertifikate GROUP BY status"),
                gruppiere("SELECT status, COUNT(*) FROM egk_karten GROUP BY status"));
    }

    private long count(String tabelle) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + tabelle, Long.class);
        return n == null ? 0L : n;
    }

    private Map<String, Long> gruppiere(String sql) {
        return jdbc.query(sql, rs -> {
            Map<String, Long> map = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                map.put(rs.getString(1), rs.getLong(2));
            }
            return map;
        });
    }

    // --- intern -------------------------------------------------------------

    /** Funktionales Pendant zum {@link BatchPreparedStatementSetter} fuer Records. */
    @FunctionalInterface
    private interface RowSetter<T> {
        void set(PreparedStatement ps, T item) throws SQLException;
    }

    private static <T> BatchPreparedStatementSetter setter(List<T> list, RowSetter<T> rowSetter) {
        return new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                rowSetter.set(ps, list.get(i));
            }

            @Override
            public int getBatchSize() {
                return list.size();
            }
        };
    }

    /** Auswertungsergebnis fuer den Stats-Endpunkt. */
    public record Stats(long krankenkassen, long leistungserbringer, long versicherte,
                        long egkKarten, long diagnosen, long zertifikate,
                        Map<String, Long> zertifikateNachStatus,
                        Map<String, Long> egkKartenNachStatus) {
    }
}
