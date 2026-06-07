package com.example.springai.demo.feature17_ebooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * FEATURE 17 – Recherche <b>gemeinfreier</b> eBooks ueber zwei legale Quellen:
 * <b>Project Gutenberg</b> (via Gutendex-API) und <b>Open Library / Internet
 * Archive</b>. Bewusst keine Paywall-Umgehung und keine "Controlled Digital
 * Lending"-Titel – nur frei zugaengliche Volltexte (gleiche Linie wie Feature 16).
 *
 * <p>Trennung wie in Feature 16: dieser Service <em>sucht</em> nur und loest pro
 * Treffer das <em>beste verfuegbare Format</em> (PDF &gt; EPUB &gt; TXT) als
 * legalen Download-Link auf. Die Bytes holt der {@link EbookDownloadService}.</p>
 *
 * <p>Wichtig fuer Open Library: nur Treffer mit {@code ebook_access = "public"}
 * und einer Internet-Archive-Id gelten als herunterladbar; alle anderen kommen
 * als reiner Verweis ({@code downloadable=false}) zurueck und werden im Frontend
 * ausgegraut – ehrlicher als sie wegzulassen.</p>
 */
@Service
public class EbookSearchService {

    private static final String GUTENDEX = "https://gutendex.com";
    private static final String OPENLIBRARY = "https://openlibrary.org";
    private static final String ARCHIVE = "https://archive.org";

    /** Pro Quelle maximal so viele Treffer in die gemeinsame Liste. */
    private static final int PER_SOURCE = 5;

    private final RestClient http;

    public EbookSearchService() {
        this(RestClient.create());
    }

    /**
     * Test-Konstruktor: erlaubt das Einschleusen eines vorkonfigurierten
     * {@link RestClient} (z.B. via {@code MockRestServiceServer}).
     */
    EbookSearchService(RestClient http) {
        this.http = http;
    }

    /** Ein Treffer der Trefferliste (das, was im Frontend zur Auswahl steht). */
    public record EbookHit(String ref, String title, String authors, Integer year,
                           String format, boolean downloadable) {
    }

    /** Aufgeloester, legaler Download-Link inkl. Ziel-Format. */
    public record ResolvedFile(String title, String url, String contentType) {
    }

    /** Internes Auswahlergebnis fuer ein Format (Anzeige-Label + URL + MIME-Typ). */
    private record FormatPick(String label, String url, String contentType) {
    }

    /**
     * Sucht in beiden Quellen und liefert die zusammengefuehrte Trefferliste
     * (zuerst Gutenberg, dann Open Library). Faellt eine Quelle aus, kommen die
     * Treffer der anderen trotzdem zurueck.
     */
    public List<EbookHit> search(String titel) {
        List<EbookHit> hits = new ArrayList<>();
        hits.addAll(searchGutendex(titel));
        hits.addAll(searchOpenLibrary(titel));
        return hits;
    }

    /**
     * Loest fuer einen Treffer-{@code ref} ({@code gutendex:1234} bzw.
     * {@code openlibrary:<ia-id>}) das beste Format als Download-Link auf. Der
     * Link wird hier server-seitig neu ermittelt – es wird also keine vom Client
     * gelieferte URL heruntergeladen ({@code Optional.empty()} = kein Volltext).
     */
    public Optional<ResolvedFile> resolve(String ref) {
        if (ref == null) {
            return Optional.empty();
        }
        if (ref.startsWith("gutendex:")) {
            return resolveGutendex(ref.substring("gutendex:".length()));
        }
        if (ref.startsWith("openlibrary:")) {
            return resolveArchive(ref.substring("openlibrary:".length()));
        }
        return Optional.empty();
    }

    // --- Gutenberg / Gutendex -------------------------------------------------

    private List<EbookHit> searchGutendex(String titel) {
        try {
            GutendexResponse resp = http.get()
                    .uri(GUTENDEX + "/books?search={s}", titel)
                    .retrieve()
                    .body(GutendexResponse.class);
            if (resp == null || resp.results() == null) {
                return List.of();
            }
            return resp.results().stream().limit(PER_SOURCE).map(EbookSearchService::toGutendexHit).toList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private Optional<ResolvedFile> resolveGutendex(String id) {
        try {
            GutendexBook book = http.get()
                    .uri(GUTENDEX + "/books/{id}", id)
                    .retrieve()
                    .body(GutendexBook.class);
            if (book == null) {
                return Optional.empty();
            }
            FormatPick pick = pickFromFormats(book.formats());
            return pick == null ? Optional.empty()
                    : Optional.of(new ResolvedFile(book.title(), pick.url(), pick.contentType()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static EbookHit toGutendexHit(GutendexBook b) {
        FormatPick pick = pickFromFormats(b.formats());
        boolean downloadable = pick != null;
        return new EbookHit("gutendex:" + b.id(), b.title(), authorString(b.authorNames()), null,
                downloadable ? pick.label() : "—", downloadable);
    }

    /** Waehlt aus einer Gutendex-Format-Map den besten Direktlink (PDF &gt; EPUB &gt; TXT). */
    private static FormatPick pickFromFormats(Map<String, String> formats) {
        if (formats == null) {
            return null;
        }
        String pdf = firstByPrefix(formats, "application/pdf");
        if (pdf != null) {
            return new FormatPick("PDF", pdf, "application/pdf");
        }
        String epub = firstByPrefix(formats, "application/epub+zip");
        if (epub != null) {
            return new FormatPick("EPUB", epub, "application/epub+zip");
        }
        String txt = firstByPrefix(formats, "text/plain");
        return txt == null ? null : new FormatPick("TXT", txt, "text/plain");
    }

    private static String firstByPrefix(Map<String, String> formats, String mimePrefix) {
        for (Map.Entry<String, String> e : formats.entrySet()) {
            if (e.getKey() != null && e.getKey().startsWith(mimePrefix)
                    && e.getValue() != null && !e.getValue().endsWith(".zip")) {
                return e.getValue();
            }
        }
        return null;
    }

    // --- Open Library / Internet Archive --------------------------------------

    private List<EbookHit> searchOpenLibrary(String titel) {
        try {
            OlResponse resp = http.get()
                    .uri(OPENLIBRARY + "/search.json?q={q}&limit={n}"
                            + "&fields=key,title,author_name,first_publish_year,ia,ebook_access",
                            titel, PER_SOURCE)
                    .retrieve()
                    .body(OlResponse.class);
            if (resp == null || resp.docs() == null) {
                return List.of();
            }
            return resp.docs().stream().map(EbookSearchService::toOlHit).toList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private Optional<ResolvedFile> resolveArchive(String iaId) {
        try {
            ArchiveMeta meta = http.get()
                    .uri(ARCHIVE + "/metadata/{id}", iaId)
                    .retrieve()
                    .body(ArchiveMeta.class);
            if (meta == null || meta.files() == null) {
                return Optional.empty();
            }
            FormatPick pick = pickArchiveFile(iaId, meta.files());
            if (pick == null) {
                return Optional.empty();
            }
            String title = meta.metadata() != null && meta.metadata().title() != null
                    ? meta.metadata().title() : iaId;
            return Optional.of(new ResolvedFile(title, pick.url(), pick.contentType()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Waehlt aus den Archive-Dateien die beste herunterladbare Datei (PDF &gt; EPUB &gt; TXT). */
    private static FormatPick pickArchiveFile(String iaId, List<ArchiveFile> files) {
        ArchiveFile pdf = firstBySuffix(files, ".pdf");
        if (pdf != null) {
            return new FormatPick("PDF", archiveUrl(iaId, pdf.name()), "application/pdf");
        }
        ArchiveFile epub = firstBySuffix(files, ".epub");
        if (epub != null) {
            return new FormatPick("EPUB", archiveUrl(iaId, epub.name()), "application/epub+zip");
        }
        ArchiveFile txt = firstBySuffix(files, ".txt");
        return txt == null ? null : new FormatPick("TXT", archiveUrl(iaId, txt.name()), "text/plain");
    }

    private static ArchiveFile firstBySuffix(List<ArchiveFile> files, String suffix) {
        for (ArchiveFile f : files) {
            if (f.name() != null && f.name().toLowerCase().endsWith(suffix)) {
                return f;
            }
        }
        return null;
    }

    private static String archiveUrl(String iaId, String name) {
        return ARCHIVE + "/download/" + iaId + "/" + name;
    }

    private static EbookHit toOlHit(OlDoc d) {
        boolean downloadable = "public".equals(d.ebookAccess()) && d.ia() != null && !d.ia().isEmpty();
        String ref = downloadable ? "openlibrary:" + d.ia().get(0) : "olref:" + d.key();
        return new EbookHit(ref, d.title(), authorString(d.authorName()), d.firstPublishYear(),
                downloadable ? "Archive.org" : "nur Verweis", downloadable);
    }

    // --- gemeinsame Helfer ----------------------------------------------------

    private static String authorString(List<String> authors) {
        if (authors == null || authors.isEmpty()) {
            return "";
        }
        String first = authors.get(0);
        return authors.size() > 1 ? first + " et al." : first;
    }

    // --- Gutendex-JSON (nur die benoetigten Felder) ---------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GutendexResponse(List<GutendexBook> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GutendexBook(Integer id, String title, List<GutendexAuthor> authors, Map<String, String> formats) {
        List<String> authorNames() {
            return authors == null ? List.of()
                    : authors.stream().map(GutendexAuthor::name).filter(java.util.Objects::nonNull).toList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GutendexAuthor(String name) {
    }

    // --- Open-Library-JSON ----------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OlResponse(List<OlDoc> docs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OlDoc(
            String key,
            String title,
            @JsonProperty("author_name") List<String> authorName,
            @JsonProperty("first_publish_year") Integer firstPublishYear,
            List<String> ia,
            @JsonProperty("ebook_access") String ebookAccess) {
    }

    // --- Internet-Archive-Metadata-JSON ---------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ArchiveMeta(ArchiveMetadata metadata, List<ArchiveFile> files) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ArchiveMetadata(String title) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ArchiveFile(String name, String format) {
    }
}
