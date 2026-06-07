package com.example.springai.demo.feature18_docsrag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class SpecCatalog {

    /** Ein ableitbares Spec-PDF aus dem Katalog. */
    public record SpecDocument(String category, String spec, String version, String pdfUrl) {
    }

    private static final Pattern LOC = Pattern.compile("<loc>\\s*(.*?)\\s*</loc>", Pattern.DOTALL);
    private static final Pattern CATEGORY = Pattern.compile("/docs/([^/]+)/");
    /** Letztes Pfadsegment: {@code {spec}_V{version}} (Version = Ziffern/Punkte). */
    private static final Pattern LEAF = Pattern.compile("/([^/]+)_V(\\d[\\d.]*)/?$");

    private SpecCatalog() {
    }

    /**
     * Parst den Sitemap-Inhalt und liefert die ableitbaren Spec-PDFs. Dubletten
     * (gleiche {@code spec}+{@code version}) werden zusammengeführt.
     */
    public static List<SpecDocument> parse(String sitemapXml) {
        Map<String, SpecDocument> byKey = new LinkedHashMap<>();
        Matcher loc = LOC.matcher(sitemapXml);
        while (loc.find()) {
            String url = loc.group(1).trim();
            if (!url.contains("/docs/") || url.endsWith("_Aend") || url.endsWith("_Aend/")) {
                continue;
            }
            Matcher leaf = LEAF.matcher(url);
            Matcher cat = CATEGORY.matcher(url);
            if (!leaf.find() || !cat.find()) {
                continue;
            }
            String spec = leaf.group(1);
            String version = leaf.group(2);
            String category = cat.group(1);
            String pdfUrl = url.replaceFirst("/docs/", "/downloads/").replaceAll("/$", "") + ".pdf";
            byKey.putIfAbsent(spec + "@" + version, new SpecDocument(category, spec, version, pdfUrl));
        }
        return new ArrayList<>(byKey.values());
    }
}
