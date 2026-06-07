package com.example.springai.demo.feature11_mcp;

import java.util.List;

import com.example.springai.demo.feature06_tools.ProductCatalogService;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

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
