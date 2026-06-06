package com.example.springai.demo.feature16_evaluator;

import java.util.List;

import com.example.springai.demo.feature10_advisors.ModelPricing;
import com.example.springai.demo.feature14_dbquery.SqlGuard;
import com.example.springai.demo.feature16_evaluator.SqlEvaluatorOptimizer.Attempt;
import com.example.springai.demo.feature16_evaluator.SqlEvaluatorOptimizer.Evaluation;
import com.example.springai.demo.feature16_evaluator.SqlEvaluatorOptimizer.Outcome;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FEATURE 16 – Evaluator-Optimizer (selbstkorrigierende KI).
 *
 * <p>Feature 14 laesst das Modell aus einer Frage SQL erzeugen und reicht es an den
 * {@link SqlGuard} weiter. Doch das Modell rät dabei Spaltennamen, missversteht das
 * Schema oder vergisst eine Bedingung – der erste Wurf ist nicht immer korrekt.
 * Hier schieben wir eine <b>Kritik-Schleife</b> dazwischen: ein zweiter "Richter"-
 * {@code ChatClient} bewertet das erzeugte SQL gegen das Schema; bei Maengeln fliesst
 * seine Begruendung als Feedback in einen neuen Generierungs-Versuch – bis das SQL
 * akzeptiert ist oder die Iterationsgrenze greift.</p>
 *
 * <p>Die Bewertung ist <b>geschichtet</b> und renutzt vorhandene Bausteine:
 * zuerst der billige, deterministische {@link SqlGuard} aus Feature 14 (rein lesend?
 * nur ein Statement?), erst danach – und nur wenn der Guard durchlaesst – der teure
 * LLM-Richter mit {@link Evaluation Structured Output} (Feature 4). So spart der
 * deterministische Vorfilter im Fehlerfall einen Modellaufruf.</p>
 *
 * <p>Zusammenspiel: Feature 13 nutzt einen Evaluator als <em>Mess-Werkzeug am Ende</em>;
 * hier wird Evaluation zum <em>aktiven Regelkreis mitten im Flow</em>.</p>
 *
 * <p>Beispiel: {@code GET /api/evaluator/sql?question=Wie viele Versicherte sind aelter als 65?}</p>
 */
@RestController
public class EvaluatorOptimizerController {

    /** Schema-Auszug – Kontext fuer Generator UND Richter (sonst halluziniert der Richter selbst). */
    private static final String SCHEMA = """
            Schema (PostgreSQL, Tabellen und wichtige Spalten):
              krankenkassen(id, ik, name, typ[GKV/PKV])
              versicherte(id, kvnr, vorname, nachname, geburtsdatum, geschlecht[M/W], plz, ort,
                          strasse, krankenkasse_id -> krankenkassen.id)
              egk_karten(id, iccsn, can, versicherte_id -> versicherte.id, gueltig_von, gueltig_bis,
                         status[AKTIV/GESPERRT/ERSETZT])
              diagnosen(id, versicherte_id -> versicherte.id, leistungserbringer_id, icd10,
                        diagnose_text, diagnose_datum)
            """;

    private static final String GENERATOR_SYSTEM = """
            Du uebersetzt eine deutschsprachige Frage in GENAU EIN rein lesendes
            PostgreSQL-SELECT. Gib nur das nackte SQL aus – ohne Erklaerung, ohne
            Markdown-Codeblock. Nutze ausschliesslich die folgenden Tabellen/Spalten.
            Das Alter berechnet sich aus geburtsdatum, nicht aus einer Spalte 'alter'.
            """ + SCHEMA;

    private static final String EVALUATOR_SYSTEM = """
            Du bist ein strenger SQL-Reviewer. Pruefe, ob das vorgelegte SELECT die
            Frage korrekt gegen das Schema beantwortet: existieren alle Spalten/Tabellen,
            ist die Logik (z.B. Altersberechnung aus geburtsdatum) richtig, fehlen
            Bedingungen? Antworte ausschliesslich im geforderten JSON-Format:
            valid=true nur, wenn das SQL fehlerfrei und schema-korrekt ist; andernfalls
            valid=false mit konkretem, umsetzbarem feedback (was ist falsch, wie korrigieren).
            """ + SCHEMA;

    private final ChatClient generatorClient;
    private final ChatClient evaluatorClient;
    private final int maxIterations;

    public EvaluatorOptimizerController(ChatClient.Builder builder) {
        this.generatorClient = builder.defaultSystem(GENERATOR_SYSTEM).build();
        // mutate() leitet einen zweiten Client mit eigenem System-Prompt aus demselben
        // Builder ab – die "zwei Rollen" desselben Modells.
        this.evaluatorClient = generatorClient.mutate().defaultSystem(EVALUATOR_SYSTEM).build();
        this.maxIterations = SqlEvaluatorOptimizer.DEFAULT_MAX_ITERATIONS;
    }

    @GetMapping("/api/evaluator/sql")
    public EvaluatorResponse optimizeSql(
            @RequestParam(defaultValue = "Wie viele Versicherte sind aelter als 65 Jahre?")
            String question) {
        // Token-Verbrauch ueber ALLE Modellaufrufe der Schleife (Generator + Richter,
        // pro Iteration) aufsummieren – analog zum Gate-Decider, nur mehrteilig.
        UsageAccumulator usage = new UsageAccumulator();
        long startNanos = System.nanoTime();
        Outcome outcome = SqlEvaluatorOptimizer.run(maxIterations, generator(question, usage), evaluator(usage));
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
        return new EvaluatorResponse(outcome.success(), outcome.sql(), outcome.attempts(),
                usage.toMetrics(latencyMs));
    }

    /** Erzeugt SQL; ab dem zweiten Versuch mit vorigem Versuch + Kritik im Prompt. */
    private SqlEvaluatorOptimizer.Generator generator(String question, UsageAccumulator usage) {
        return (previousSql, feedback) -> {
            ChatResponse response;
            if (feedback == null) {
                response = generatorClient.prompt()
                        .user(u -> u.text("Frage: {q}").param("q", question))
                        .call()
                        .chatResponse();
            } else {
                response = generatorClient.prompt()
                        .user(u -> u.text("""
                                Frage: {q}
                                Dein vorheriger Versuch:
                                {sql}
                                Kritik des Reviewers:
                                {fb}
                                Erzeuge eine korrigierte Fassung.""")
                                .param("q", question)
                                .param("sql", previousSql)
                                .param("fb", feedback))
                        .call()
                        .chatResponse();
            }
            usage.add(response);
            return stripCodeFences(response.getResult().getOutput().getText());
        };
    }

    /** Geschichtete Bewertung: erst deterministischer SqlGuard, dann LLM-Richter. */
    private SqlEvaluatorOptimizer.Evaluator evaluator(UsageAccumulator usage) {
        // Strukturierte Ausgabe manuell (statt .entity(...)): so kommen Evaluation UND
        // ChatResponse aus EINEM Aufruf – die ChatResponse liefert den Token-Verbrauch.
        BeanOutputConverter<Evaluation> converter = new BeanOutputConverter<>(Evaluation.class);
        return sql -> {
            try {
                SqlGuard.sanitize(sql);
            } catch (IllegalArgumentException e) {
                // Billiger Vorfilter schlaegt an – kein Modellaufruf noetig.
                return new Evaluation(false, "Sicherheits-Check fehlgeschlagen: " + e.getMessage());
            }
            ChatResponse response = evaluatorClient.prompt()
                    .user(u -> u.text("Bewerte dieses SQL:\n{sql}\n\n{format}")
                            .param("sql", sql)
                            .param("format", converter.getFormat()))
                    .call()
                    .chatResponse();
            usage.add(response);
            return converter.convert(response.getResult().getOutput().getText());
        };
    }

    /**
     * Entfernt einen umschliessenden Markdown-Codeblock ({@code ```sql ... ```}), den
     * Modelle trotz Anweisung oft setzen. Ohne das wuerde der SqlGuard die Backticks
     * als unzulaessig ablehnen.
     */
    static String stripCodeFences(String raw) {
        String s = raw.strip();
        if (s.startsWith("```")) {
            int firstLineBreak = s.indexOf('\n');
            if (firstLineBreak >= 0) {
                s = s.substring(firstLineBreak + 1);
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3);
            }
        }
        return s.strip();
    }
    public record EvaluatorResponse(boolean success, String sql, List<Attempt> attempts,
                                    EvalMetrics metrics) {
    }

    public record EvalMetrics(Integer inputTokens, Integer outputTokens, Integer totalTokens,
                              long latencyMs, Double costUsd) {
    }

    private static final class UsageAccumulator {
        private int inputTokens;
        private int outputTokens;
        private int totalTokens;
        private boolean any;
        private String model;

        void add(ChatResponse response) {
            if (response == null || response.getMetadata() == null) {
                return;
            }
            if (response.getMetadata().getModel() != null) {
                model = response.getMetadata().getModel();
            }
            Usage u = response.getMetadata().getUsage();
            if (u == null) {
                return;
            }
            if (u.getPromptTokens() != null) {
                inputTokens += u.getPromptTokens();
            }
            if (u.getCompletionTokens() != null) {
                outputTokens += u.getCompletionTokens();
            }
            if (u.getTotalTokens() != null) {
                totalTokens += u.getTotalTokens();
            }
            any = true;
        }

        EvalMetrics toMetrics(long latencyMs) {
            if (!any) {
                return new EvalMetrics(null, null, null, latencyMs, null);
            }
            Double costUsd = ModelPricing.costUsd(model, inputTokens, outputTokens);
            return new EvalMetrics(inputTokens, outputTokens, totalTokens, latencyMs, costUsd);
        }
    }
}
