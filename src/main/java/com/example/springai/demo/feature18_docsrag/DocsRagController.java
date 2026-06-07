package com.example.springai.demo.feature18_docsrag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/docs-rag")
@Profile("specs")
public class DocsRagController {

    private final CatalogService catalogService;
    private final DocsRagWorker worker;
    private final SpecSearchService search;

    public DocsRagController(CatalogService catalogService, DocsRagWorker worker,
                             SpecSearchService search) {
        this.catalogService = catalogService;
        this.worker = worker;
        this.search = search;
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

    /**
     * Hybrid-Suche ueber die ingestierten Spec-Chunks: lexikalisch (exakt/Volltext)
     * fusioniert mit semantischer Vektor-Naehe. Reines Retrieval ohne LLM/API-Key.
     */
    @GetMapping("/search")
    public List<SpecSearchService.Hit> search(@RequestParam String question,
                                              @RequestParam(defaultValue = "8") int topK) {
        return search.search(question, topK);
    }
}
