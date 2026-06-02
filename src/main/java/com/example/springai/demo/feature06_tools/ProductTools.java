package com.example.springai.demo.feature06_tools;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Werkzeuge, die den fachlichen {@link ProductCatalogService} für das Modell
 * zugänglich machen.
 *
 * <p>Das ist das <b>produktiv relevante Muster</b> beim Tool Calling: Ein Tool ist
 * eine dünne, gut dokumentierte Fassade vor vorhandener Geschäftslogik. Weil
 * {@code ProductTools} eine Spring-Bean ist, kann es per Dependency Injection auf
 * beliebige Services, Repositories oder Clients zugreifen – das Modell ruft am
 * Ende ganz normalen, getesteten Anwendungscode auf.</p>
 */
@Component
public class ProductTools {

    private final ProductCatalogService catalog;

    public ProductTools(ProductCatalogService catalog) {
        this.catalog = catalog;
    }

    @Tool(description = "Liefert den aktuellen Lagerbestand (Stueckzahl) eines Produkts anhand seines Namens.")
    public String getProductStock(
            @ToolParam(description = "Name des Produkts, z.B. 'Laptop' oder 'Monitor'") String productName) {
        return catalog.stockFor(productName)
                .map(count -> "%d Stueck von '%s' auf Lager.".formatted(count, productName))
                .orElse("Produkt '%s' ist nicht im Katalog.".formatted(productName));
    }

    @Tool(description = "Listet alle im Katalog verfuegbaren Produktnamen auf.")
    public List<String> listAvailableProducts() {
        return catalog.productNames();
    }
}
