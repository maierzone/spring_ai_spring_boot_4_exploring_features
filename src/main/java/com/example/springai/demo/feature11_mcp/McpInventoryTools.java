package com.example.springai.demo.feature11_mcp;

import java.util.List;

import com.example.springai.demo.feature06_tools.ProductCatalogService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * FEATURE 11 (Teil A) – MCP-Server: fachliche Tools, die als Model-Context-Protocol-
 * Tools veroeffentlicht werden.
 *
 * <p>Dies ist bewusst dieselbe Idee wie beim klassischen Tool Calling (Feature 6):
 * ein Tool ist eine duenne, gut beschriebene Fassade vor vorhandener Geschaeftslogik
 * (hier dem {@link ProductCatalogService}). Der Unterschied liegt nur in der
 * Reichweite: Statt das Tool <em>innerhalb</em> dieser Anwendung an einen ChatClient
 * zu haengen, machen wir es ueber einen MCP-Server <em>prozess-/netzwerkuebergreifend</em>
 * fuer beliebige MCP-faehige Clients (Claude Desktop, andere Agenten, weitere Spring-
 * AI-Apps) zugaenglich.</p>
 *
 * <p>Veroeffentlicht werden diese Methoden durch die {@code ToolCallbackProvider}-Bean
 * in {@link McpServerConfig}; der {@code spring-ai-starter-mcp-server-webmvc} stellt
 * sie dann automatisch unter dem SSE-Endpunkt des MCP-Servers bereit.</p>
 */
@Component
public class McpInventoryTools {

    private final ProductCatalogService catalog;

    public McpInventoryTools(ProductCatalogService catalog) {
        this.catalog = catalog;
    }

    @Tool(description = "Liefert den aktuellen Lagerbestand (Stueckzahl) eines Produkts anhand seines Namens.")
    public String getStock(
            @ToolParam(description = "Name des Produkts, z.B. 'Laptop' oder 'Monitor'") String productName) {
        return catalog.stockFor(productName)
                .map(count -> "%d Stueck von '%s' auf Lager.".formatted(count, productName))
                .orElse("Produkt '%s' ist nicht im Katalog.".formatted(productName));
    }

    @Tool(description = "Listet alle im Katalog verfuegbaren Produktnamen auf.")
    public List<String> listProducts() {
        return catalog.productNames();
    }
}
