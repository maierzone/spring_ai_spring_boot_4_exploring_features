package com.example.springai.demo.testdata;

import com.example.springai.demo.testdata.EgkTestDataRepository.Stats;
import com.example.springai.demo.testdata.TestDataGenerator.Dataset;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-Schnittstelle fuer den eGK-/Gesundheits-/PKI-Testdaten-Generator.
 *
 * <p>Befuellt PostgreSQL on demand mit einem grossen, rein synthetischen
 * (DSGVO-sicheren) Datenbestand – ideal, um Queries, Reports und Analytics auf
 * realistisch strukturierten, aber unbedenklichen Daten auszuprobieren.</p>
 *
 * <ul>
 *   <li>{@code POST /api/testdata/generate?count=10000&seed=42} – erzeugt den
 *       Datensatz deterministisch und ersetzt den bisherigen Bestand.</li>
 *   <li>{@code GET  /api/testdata/stats} – Zeilenzahlen je Tabelle + Status-Verteilung.</li>
 * </ul>
 */
@RestController
public class TestDataController {

    private final TestDataGenerator generator;
    private final EgkTestDataRepository repository;

    public TestDataController(TestDataGenerator generator, EgkTestDataRepository repository) {
        this.generator = generator;
        this.repository = repository;
    }

    /**
     * Generiert und persistiert einen Datensatz. {@code count} steuert die Anzahl
     * der Versicherten und damit das Gesamtvolumen; {@code seed} macht den Lauf
     * reproduzierbar.
     *
     * @param count Anzahl Versicherte (Standard 10.000)
     * @param seed  Zufalls-Seed (Standard {@link TestDataGenerator#DEFAULT_SEED})
     */
    @PostMapping("/api/testdata/generate")
    public Stats generate(@RequestParam(defaultValue = "10000") int count,
                          @RequestParam(defaultValue = "42") long seed) {
        Dataset dataset = generator.generate(count, seed);
        repository.replaceAll(dataset);
        return repository.stats();
    }

    /** Liefert die aktuellen Zeilenzahlen je Tabelle und die Status-Verteilungen. */
    @GetMapping("/api/testdata/stats")
    public Stats stats() {
        return repository.stats();
    }
}
