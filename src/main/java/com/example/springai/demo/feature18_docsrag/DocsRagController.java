package com.example.springai.demo.feature18_docsrag;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FEATURE 18 – REST-Steuerung der docs_rag-Pipeline für die UI.
 *
 * <ul>
 *   <li>{@code GET  /api/docs-rag/stats} – die 4 Zähler + Laufzustand (Polling).</li>
 *   <li>{@code POST /api/docs-rag/seed}  – Katalog aus der Sitemap nachladen.</li>
 *   <li>{@code POST /api/docs-rag/start?batch=N} – Dauerlauf starten (N=1/5/10).</li>
 *   <li>{@code POST /api/docs-rag/stop}  – Dauerlauf pausieren.</li>
 * </ul>
 *
 * <p>Nur unter Profil {@code specs} aktiv (Pipeline braucht pgvector + Embedding).</p>
 */
@RestController
@RequestMapping("/api/docs-rag")
@Profile("specs")
public class DocsRagController {

    private final CatalogService catalogService;
    private final DocsRagWorker worker;

    public DocsRagController(CatalogService catalogService, DocsRagWorker worker) {
        this.catalogService = catalogService;
        this.worker = worker;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return worker.stats();
    }

    @PostMapping("/seed")
    public Map<String, Object> seed() {
        int inserted = catalogService.seed();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inserted", inserted);
        result.putAll(worker.stats());
        return result;
    }

    @PostMapping("/start")
    public Map<String, Object> start(@RequestParam(defaultValue = "1") int batch) {
        worker.start(batch);
        return worker.stats();
    }

    @PostMapping("/stop")
    public Map<String, Object> stop() {
        worker.stop();
        return worker.stats();
    }
}
