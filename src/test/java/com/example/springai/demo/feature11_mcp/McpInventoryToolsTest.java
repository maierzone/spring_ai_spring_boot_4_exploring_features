package com.example.springai.demo.feature11_mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.springai.demo.feature06_tools.ProductCatalogService;

import org.junit.jupiter.api.Test;

/**
 * Die ueber MCP veroeffentlichten Tools sind – wie beim klassischen Tool Calling –
 * ganz normale Java-Methoden vor einem Fach-Service und damit ohne Modell, Netzwerk
 * oder MCP-Transport testbar.
 */
class McpInventoryToolsTest {

    private final McpInventoryTools tools = new McpInventoryTools(new ProductCatalogService());

    @Test
    void liefertBestandFuerBekanntesProdukt() {
        assertThat(tools.getStock("Monitor")).contains("7").contains("Monitor");
    }

    @Test
    void meldetUnbekanntesProdukt() {
        assertThat(tools.getStock("Drucker")).contains("nicht im Katalog");
    }

    @Test
    void listetAlleProdukteAuf() {
        assertThat(tools.listProducts()).containsExactly("laptop", "maus", "monitor", "tastatur");
    }
}
