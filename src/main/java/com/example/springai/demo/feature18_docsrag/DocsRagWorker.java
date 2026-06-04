package com.example.springai.demo.feature18_docsrag;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * FEATURE 18 – Hintergrund-Worker der Spec-Ingestion-Pipeline.
 *
 * <p>Ein {@link Scheduled}-Tick zieht – solange der Worker "läuft" – pro Durchlauf
 * bis zu {@code batchSize} (1/5/10) Dokumente mit {@code status='incoming'} aus
 * {@code docs_rag}, lädt das PDF live von gemspec herunter, verarbeitet es über den
 * {@link SpecPdfProcessor} in den Vektorspeicher und setzt den Status auf
 * {@code processed} bzw. – bei Download-/Parse-Fehlern (z.&nbsp;B. 404) – auf
 * {@code error}. Ist keine {@code incoming}-Zeile mehr da, pausiert der Worker
 * selbsttätig.</p>
 *
 * <p>Start/Stop und Batchgröße steuert der {@link DocsRagController} (UI-Button +
 * Dropdown). Nur unter Profil {@code specs} aktiv.</p>
 */
@Component
@Profile("specs")
public class DocsRagWorker {

    private static final Logger log = LoggerFactory.getLogger(DocsRagWorker.class);

    private final JdbcTemplate jdbcTemplate;
    private final SpecPdfProcessor processor;
    private final RestClient restClient = RestClient.create();

    private volatile boolean running = false;
    private volatile int batchSize = 1;

    public DocsRagWorker(JdbcTemplate jdbcTemplate, SpecPdfProcessor processor) {
        this.jdbcTemplate = jdbcTemplate;
        this.processor = processor;
    }

    /** Startet den Dauerlauf mit der gewählten Batchgröße (erlaubt: 1, 5, 10). */
    public synchronized void start(int batch) {
        this.batchSize = (batch == 5 || batch == 10) ? batch : 1;
        this.running = true;
        log.info("DocsRagWorker gestartet (Batchgröße {}).", this.batchSize);
    }

    /** Pausiert den Dauerlauf (laufender Batch wird noch zu Ende verarbeitet). */
    public synchronized void stop() {
        this.running = false;
        log.info("DocsRagWorker gestoppt.");
    }

    /**
     * Verarbeitungs-Tick. Läuft dank {@code fixedDelay} nie überlappend: der
     * nächste Tick beginnt erst {@code delay} nach Abschluss des vorigen.
     */
    @Scheduled(fixedDelayString = "${docsrag.worker.delay-ms:2000}")
    public void tick() {
        if (!running) {
            return;
        }
        List<Map<String, Object>> batch = jdbcTemplate.queryForList(
                "SELECT id, spec, version, pdf_url FROM docs_rag WHERE status = 'incoming' ORDER BY id LIMIT " + batchSize);
        if (batch.isEmpty()) {
            running = false;
            log.info("Keine 'incoming'-Dokumente mehr – Worker pausiert.");
            return;
        }
        for (Map<String, Object> row : batch) {
            processOne(row);
        }
    }

    private void processOne(Map<String, Object> row) {
        long id = ((Number) row.get("id")).longValue();
        String spec = (String) row.get("spec");
        String version = (String) row.get("version");
        String url = (String) row.get("pdf_url");
        try {
            byte[] pdf = restClient.get().uri(url).retrieve().body(byte[].class);
            if (pdf == null || pdf.length == 0) {
                throw new IllegalStateException("Leere Download-Antwort");
            }
            Path tmp = Files.createTempFile("docsrag-", ".pdf");
            try {
                Files.write(tmp, pdf);
                int chunks = processor.process(new FileSystemResource(tmp), spec, version, spec + "_V" + version + ".pdf");
                jdbcTemplate.update("UPDATE docs_rag SET status = 'processed', chunks = ?, "
                        + "processed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?", chunks, id);
                log.info("processed {} v{} ({} Chunks).", spec, version, chunks);
            }
            finally {
                Files.deleteIfExists(tmp);
            }
        }
        catch (Exception e) {
            String msg = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
            if (msg.length() > 1990) {
                msg = msg.substring(0, 1990);
            }
            jdbcTemplate.update("UPDATE docs_rag SET status = 'error', error_message = ?, "
                    + "updated_at = CURRENT_TIMESTAMP WHERE id = ?", msg, id);
            log.warn("error {} v{}: {}", spec, version, msg);
        }
    }

    /** Die 4 Zähler plus Laufzustand für die UI. */
    public Map<String, Object> stats() {
        long incoming = 0;
        long processed = 0;
        long error = 0;
        for (Map<String, Object> r : jdbcTemplate.queryForList(
                "SELECT status, COUNT(*) AS c FROM docs_rag GROUP BY status")) {
            long c = ((Number) r.get("c")).longValue();
            switch ((String) r.get("status")) {
                case "incoming" -> incoming = c;
                case "processed" -> processed = c;
                case "error" -> error = c;
                default -> {
                }
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", incoming + processed + error);
        stats.put("incoming", incoming);
        stats.put("processed", processed);
        stats.put("error", error);
        stats.put("running", running);
        stats.put("batchSize", batchSize);
        return stats;
    }
}
