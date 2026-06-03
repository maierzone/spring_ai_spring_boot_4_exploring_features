package com.example.springai.demo.feature16_evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.example.springai.demo.feature16_evaluator.SqlEvaluatorOptimizer.Evaluation;
import com.example.springai.demo.feature16_evaluator.SqlEvaluatorOptimizer.Outcome;

/**
 * Prueft die Konvergenz-Logik des Evaluator-Optimizer-Patterns OHNE echten
 * Modellaufruf: Generator und Evaluator sind deterministische Lambdas. So lassen
 * sich die drei Verhaltensweisen, auf die es ankommt, exakt nachstellen –
 * Sofort-Erfolg, Verbesserung nach Feedback und harter Abbruch an der Grenze.
 */
class SqlEvaluatorOptimizerTest {

    private static final Evaluation OK = new Evaluation(true, "passt");
    private static final Evaluation NOK = new Evaluation(false, "Spalte 'alter' existiert nicht.");

    @Test
    void stopptSofortWennErsterVersuchAkzeptiertWird() {
        Outcome outcome = SqlEvaluatorOptimizer.run(
                3,
                (prev, feedback) -> "SELECT count(*) FROM versicherte",
                sql -> OK);

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.attempts()).hasSize(1);
        assertThat(outcome.sql()).isEqualTo("SELECT count(*) FROM versicherte");
    }

    @Test
    void verbessertNachFeedbackUndKonvergiert() {
        // Generator: erster Wurf falsch, nach Erhalt von Feedback der korrigierte.
        SqlEvaluatorOptimizer.Generator generator = (previousSql, feedback) ->
                feedback == null
                        ? "SELECT count(*) FROM versicherte WHERE alter > 65"
                        : "SELECT count(*) FROM versicherte WHERE geburtsdatum < now() - interval '65 years'";
        // Evaluator akzeptiert nur die Fassung ohne die nicht existierende Spalte 'alter'.
        SqlEvaluatorOptimizer.Evaluator evaluator = sql -> sql.contains("alter >") ? NOK : OK;

        Outcome outcome = SqlEvaluatorOptimizer.run(3, generator, evaluator);

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.attempts()).hasSize(2);
        // Das Feedback des ersten Urteils muss in den zweiten Versuch geflossen sein.
        assertThat(outcome.attempts().get(0).evaluation().valid()).isFalse();
        assertThat(outcome.sql()).contains("geburtsdatum");
    }

    @Test
    void brichtNachMaxIterationsErfolglosAb() {
        AtomicInteger calls = new AtomicInteger();
        Outcome outcome = SqlEvaluatorOptimizer.run(
                3,
                (prev, feedback) -> "SELECT * FROM versicherte WHERE alter > 65 #" + calls.incrementAndGet(),
                sql -> NOK);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.attempts()).hasSize(3);
        assertThat(calls).hasValue(3);
    }

    @Test
    void maxIterationsKleinerEinsIstUnzulaessig() {
        assertThatThrownBy(() -> SqlEvaluatorOptimizer.run(0, (p, f) -> "x", sql -> OK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void entferntMarkdownCodeblockUmDasSql() {
        String wrapped = "```sql\nSELECT count(*) FROM versicherte\n```";
        assertThat(EvaluatorOptimizerController.stripCodeFences(wrapped))
                .isEqualTo("SELECT count(*) FROM versicherte");
        // Nacktes SQL bleibt unveraendert.
        assertThat(EvaluatorOptimizerController.stripCodeFences("SELECT 1"))
                .isEqualTo("SELECT 1");
    }
}
