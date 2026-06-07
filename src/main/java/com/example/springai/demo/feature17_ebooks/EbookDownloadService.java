package com.example.springai.demo.feature17_ebooks;

import java.util.Optional;

import com.example.springai.demo.feature16_downloads.DownloadRepository;
import com.example.springai.demo.feature17_ebooks.EbookSearchService.ResolvedFile;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * FEATURE 17 – Laedt den legalen Volltext eines eBook-Treffers herunter und legt
 * die Bytes ueber das (mit Feature 16 geteilte) {@link DownloadRepository} ab –
 * mit {@code source_type = "EBOOK"} als Diskriminator.
 *
 * <p>Sicherheits-/Robustheits-Hinweis: Das Frontend schickt nur die Treffer-Refs
 * ({@code gutendex:...} / {@code openlibrary:...}). Den eigentlichen Download-Link
 * loest der Server selbst ueber {@link EbookSearchService} auf – so wird keine vom
 * Client gelieferte URL blind heruntergeladen (kein SSRF ueber Nutzer-URLs).</p>
 */
@Service
public class EbookDownloadService {

    /** Obergrenze fuer ein einzelnes eBook – schuetzt Heap/DB vor Ausreissern. */
    private static final int MAX_EBOOK_BYTES = 50 * 1024 * 1024;

    private final EbookSearchService search;
    private final DownloadRepository repository;
    private final RestClient http = RestClient.create();

    public EbookDownloadService(EbookSearchService search, DownloadRepository repository) {
        this.search = search;
        this.repository = repository;
    }

    /** Ergebnis eines einzelnen Download-Versuchs (pro ausgewaehltem Treffer). */
    public record DownloadOutcome(String ref, String title, Long id, boolean stored, String detail) {
    }

    /** Loest, laedt und speichert ein eBook anhand seines Treffer-{@code ref}. */
    public DownloadOutcome download(String ref) {
        Optional<ResolvedFile> resolved = search.resolve(ref);
        if (resolved.isEmpty()) {
            return new DownloadOutcome(ref, ref, null, false, "Kein freier Volltext gefunden.");
        }
        ResolvedFile file = resolved.get();
        byte[] bytes;
        try {
            bytes = http.get().uri(file.url()).retrieve().body(byte[].class);
        } catch (RuntimeException e) {
            return new DownloadOutcome(ref, file.title(), null, false, "Download fehlgeschlagen: " + e.getMessage());
        }
        if (bytes == null || bytes.length == 0) {
            return new DownloadOutcome(ref, file.title(), null, false, "Leere Antwort vom Download-Link.");
        }
        if (bytes.length > MAX_EBOOK_BYTES) {
            return new DownloadOutcome(ref, file.title(), null, false,
                    "Datei zu gross (" + (bytes.length / (1024 * 1024)) + " MB > 50 MB).");
        }
        if (!signatureMatches(file.contentType(), bytes)) {
            return new DownloadOutcome(ref, file.title(), null, false,
                    "Inhalt passt nicht zum erwarteten Format (Link fuehrt vermutlich auf eine HTML-Seite).");
        }
        long id = repository.save("EBOOK", file.title(), ref, file.contentType(), bytes);
        return new DownloadOutcome(ref, file.title(), id, true, "Gespeichert (" + bytes.length + " Bytes).");
    }

    /**
     * Pruefung der Datei-Signatur passend zum aufgeloesten Format, damit keine
     * Landing-Pages in der Bibliothek landen: PDF -&gt; {@code %PDF}, EPUB -&gt;
     * ZIP-Magic {@code PK}; reiner Text wird nur auf Nicht-Leere geprueft.
     */
    private static boolean signatureMatches(String contentType, byte[] b) {
        return switch (contentType) {
            case "application/pdf" -> b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
            case "application/epub+zip" -> b.length >= 2 && b[0] == 'P' && b[1] == 'K';
            default -> true; // text/plain o.ae.: keine verlaessliche Magic-Number
        };
    }
}
