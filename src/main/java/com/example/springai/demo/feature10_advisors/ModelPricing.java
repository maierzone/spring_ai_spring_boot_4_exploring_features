package com.example.springai.demo.feature10_advisors;

import java.util.Map;

/**
 * FEATURE 10 – Preis-Maptabelle für die Kostenabschätzung der Modellaufrufe.
 *
 * <p>Bildet eine Modell-Id auf den Listenpreis ab (USD pro 1.000.000 Tokens,
 * getrennt nach Input/Prompt- und Output/Completion-Tokens). Der
 * {@link MetricsLoggingAdvisor} liest die tatsächliche Token-Nutzung aus den
 * Antwort-Metadaten und multipliziert sie mit diesem Tarif, um die Kosten eines
 * einzelnen Aufrufs auszuweisen.</p>
 *
 * <p>Bewusst eine kleine, statische Tabelle (kein konfigurierbarer Store): die
 * Preise ändern sich selten und sind hier nachvollziehbar versioniert.</p>
 */
public final class ModelPricing {

    /** Listenpreis eines Modells in USD pro 1.000.000 Tokens. */
    public record Price(double inputPerMillion, double outputPerMillion) {
    }

    // Stand: offizielle Anthropic-Listenpreise (USD / 1 Mio. Tokens).
    private static final Map<String, Price> PRICES = Map.of(
            "claude-opus-4-8", new Price(5.00, 25.00),
            "claude-opus-4-7", new Price(5.00, 25.00),
            "claude-opus-4-6", new Price(5.00, 25.00),
            "claude-sonnet-4-6", new Price(3.00, 15.00),
            "claude-haiku-4-5", new Price(1.00, 5.00));

    // Fallback für unbekannte Modelle: günstigster (Haiku-)Tarif, damit Kosten
    // nicht überschätzt werden – statt gar keiner Angabe.
    private static final Price FALLBACK = new Price(1.00, 5.00);

    private ModelPricing() {
    }

    /**
     * Tarif zur Modell-Id. Trifft auch datierte Varianten (z.&nbsp;B.
     * {@code claude-haiku-4-5-20251001}) über einen Präfix-Vergleich.
     */
    static Price priceFor(String model) {
        if (model == null) {
            return FALLBACK;
        }
        Price exact = PRICES.get(model);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, Price> entry : PRICES.entrySet()) {
            if (model.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return FALLBACK;
    }

    /** Kosten eines Aufrufs in USD für die gegebene Token-Nutzung. */
    public static double costUsd(String model, long inputTokens, long outputTokens) {
        Price price = priceFor(model);
        return inputTokens / 1_000_000.0 * price.inputPerMillion()
                + outputTokens / 1_000_000.0 * price.outputPerMillion();
    }
}
