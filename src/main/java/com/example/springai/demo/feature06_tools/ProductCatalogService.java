package com.example.springai.demo.feature06_tools;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class ProductCatalogService {

    /** In-Memory-Lagerbestand: Produktname (kleingeschrieben) -> Stückzahl. */
    private static final Map<String, Integer> STOCK = Map.of(
            "laptop", 12,
            "tastatur", 0,
            "maus", 45,
            "monitor", 7);

    /** Liefert den Lagerbestand eines Produkts, falls bekannt. */
    public Optional<Integer> stockFor(String productName) {
        if (productName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(STOCK.get(productName.trim().toLowerCase()));
    }

    /** Alle bekannten Produktnamen. */
    public List<String> productNames() {
        return STOCK.keySet().stream().sorted().toList();
    }
}
