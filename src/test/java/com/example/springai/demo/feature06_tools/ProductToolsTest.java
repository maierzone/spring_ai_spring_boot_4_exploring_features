package com.example.springai.demo.feature06_tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Die Tools sind dünne Fassaden vor dem {@link ProductCatalogService} und lassen
 * sich als ganz normaler Java-Code testen – unabhängig vom Modell.
 */
class ProductToolsTest {

    private final ProductTools tools = new ProductTools(new ProductCatalogService());

    @Test
    void liefertBestandFuerBekanntesProdukt() {
        // Lookup ist case-insensitiv; der gemeldete Bestand ist 7.
        assertThat(tools.getProductStock("Monitor")).contains("7").contains("Monitor");
    }

    @Test
    void meldetUnbekanntesProdukt() {
        assertThat(tools.getProductStock("Drucker")).contains("nicht im Katalog");
    }

    @Test
    void listetAlleProdukteSortiertAuf() {
        assertThat(tools.listAvailableProducts())
                .containsExactly("laptop", "maus", "monitor", "tastatur");
    }
}
