package com.example.springai.demo.feature16_downloads;

import java.util.Optional;

import com.example.springai.demo.feature16_downloads.PaperSearchService.ResolvedPdf;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * FEATURE 16 – Laedt den legalen OA-Volltext eines OpenAlex-Treffers herunter und
 * legt die Bytes ueber das {@link DownloadRepository} ab.
 *
 * <p>Sicherheits-/Robustheits-Hinweis: Das Frontend schickt nur die OpenAlex-Ids
 * der ausgewaehlten Treffer. Den eigentlichen PDF-Link loest der Server selbst
 * ueber {@link PaperSearchService} auf – so wird keine vom Client gelieferte URL
 * blind heruntergeladen (kein SSRF ueber beliebige Nutzer-URLs).</p>
 */
@Service
public class PaperDownloadService {

    /** Obergrenze fuer ein einzelnes PDF – schuetzt Heap/DB vor Ausreissern. */
    private static final int MAX_PDF_BYTES = 30 * 1024 * 1024;

    private final PaperSearchService search;
    private final DownloadRepository repository;
    private final RestClient http = RestClient.create();

    public PaperDownloadService(PaperSearchService search, DownloadRepository repository) {
        this.search = search;
        this.repository = repository;
    }

    /** Ergebnis eines einzelnen Download-Versuchs (pro ausgewaehltem Treffer). */
    public record DownloadOutcome(String ref, String title, Long id, boolean stored, String detail) {
    }

    /** Loest, laedt und speichert ein Paper anhand seiner OpenAlex-Id ({@code W...}). */
    public DownloadOutcome download(String ref) {
        Optional<ResolvedPdf> resolved = search.resolvePdf(ref);
        if (resolved.isEmpty()) {
            return new DownloadOutcome(ref, ref, null, false, "Kein freier OA-Volltext gefunden.");
        }
        ResolvedPdf pdf = resolved.get();
        byte[] bytes;
        try {
            bytes = http.get().uri(pdf.pdfUrl()).retrieve().body(byte[].class);
        } catch (RuntimeException e) {
            return new DownloadOutcome(ref, pdf.title(), null, false, "Download fehlgeschlagen: " + e.getMessage());
        }
        if (bytes == null || bytes.length == 0) {
            return new DownloadOutcome(ref, pdf.title(), null, false, "Leere Antwort vom Volltext-Link.");
        }
        if (bytes.length > MAX_PDF_BYTES) {
            return new DownloadOutcome(ref, pdf.title(), null, false,
                    "Datei zu gross (" + (bytes.length / (1024 * 1024)) + " MB > 30 MB).");
        }
        if (!isPdf(bytes)) {
            return new DownloadOutcome(ref, pdf.title(), null, false,
                    "Kein direktes PDF (Link fuehrt vermutlich auf eine HTML-Seite).");
        }
        long id = repository.save("PAPER", pdf.title(), "openalex:" + ref, "application/pdf", bytes);
        return new DownloadOutcome(ref, pdf.title(), id, true, "Gespeichert (" + bytes.length + " Bytes).");
    }

    /** Pruefung auf die PDF-Signatur {@code %PDF}, damit keine Landing-Pages landen. */
    private static boolean isPdf(byte[] b) {
        return b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
    }
}
