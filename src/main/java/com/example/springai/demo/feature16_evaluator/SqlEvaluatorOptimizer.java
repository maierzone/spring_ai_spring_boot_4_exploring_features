package com.example.springai.demo.feature16_evaluator;

import java.util.ArrayList;
import java.util.List;


public final class SqlEvaluatorOptimizer {

    /** Vorgabe-Obergrenze fuer die Verbesserungsschleife (Schutz vor Endlosschleife/Kosten). */
    public static final int DEFAULT_MAX_ITERATIONS = 3;

    private SqlEvaluatorOptimizer() {
    }

    /** Urteil des Evaluators ueber einen einzelnen Vorschlag. */
    public record Evaluation(boolean valid, String feedback) {
    }

    /** Ein einzelner Schleifendurchlauf: erzeugter Vorschlag samt Urteil. */
    public record Attempt(int iteration, String sql, Evaluation evaluation) {
    }

    /**
     * Gesamtergebnis der Schleife.
     *
     * @param success  ob am Ende ein vom Evaluator akzeptierter Vorschlag vorlag
     * @param sql      der letzte (bei Erfolg: akzeptierte) Vorschlag
     * @param attempts vollstaendige Historie – die "Beweiskette" der Verbesserung
     */
    public record Outcome(boolean success, String sql, List<Attempt> attempts) {
    }

    /** Erzeugt einen Vorschlag; beim ersten Lauf sind {@code previousSql}/{@code feedback} {@code null}. */
    @FunctionalInterface
    public interface Generator {
        String generate(String previousSql, String feedback);
    }

    /** Bewertet einen Vorschlag. */
    @FunctionalInterface
    public interface Evaluator {
        Evaluation evaluate(String sql);
    }

    /**
     * Fuehrt die Generate-Evaluate-Optimize-Schleife aus.
     *
     * @param maxIterations harte Obergrenze ({@code >= 1}) – terminiert die Schleife garantiert
     * @param generator     erzeugt (ggf. verbesserte) Vorschlaege
     * @param evaluator     bewertet jeden Vorschlag
     * @return das Ergebnis samt vollstaendiger Versuchshistorie
     */
    public static Outcome run(int maxIterations, Generator generator, Evaluator evaluator) {
        if (maxIterations < 1) {
            throw new IllegalArgumentException("maxIterations muss >= 1 sein.");
        }
        List<Attempt> attempts = new ArrayList<>();
        String sql = null;
        String feedback = null;

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            sql = generator.generate(sql, feedback);
            Evaluation evaluation = evaluator.evaluate(sql);
            attempts.add(new Attempt(iteration, sql, evaluation));

            if (evaluation.valid()) {
                return new Outcome(true, sql, List.copyOf(attempts));
            }
            // Kritik des Evaluators wird zum Kontext des naechsten Versuchs.
            feedback = evaluation.feedback();
        }
        // Grenze erreicht, ohne dass der Evaluator je zugestimmt hat.
        return new Outcome(false, sql, List.copyOf(attempts));
    }
}
