package com.example.springai.demo.feature16_downloads;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * FEATURE 16 – Recherche wissenschaftlicher Paper ueber <b>OpenAlex</b> (legaler
 * Open-Access-Index, keine Paywall-Umgehung).
 *
 * <p>Bewusste Trennung: dieser Service <em>sucht</em> nur und loest den legalen
 * Volltext-Link auf. Das Herunterladen/Speichern der Bytes macht der
 * {@link PaperDownloadService}. So bleibt die HTTP-Recherche testbar und der
 * eigentliche Download ein getrennter, ueber REST angestossener Schritt.</p>
 *
 * <p>Fuer den OpenAlex-/Unpaywall-"polite pool" wird eine Kontakt-Mail als
 * Query-Parameter mitgeschickt (Property {@code app.openalex.mailto}); leer =
 * anonymer Zugriff.</p>
 */
@Service
public class PaperSearchService {

    private static final String OPENALEX = "https://api.openalex.org";
    private static final String UNPAYWALL = "https://api.unpaywall.org/v2";

    private final RestClient http;
    private final String mailto;

    @Autowired
    public PaperSearchService(@Value("${app.openalex.mailto:}") String mailto) {
        this(RestClient.create(), mailto);
    }

    /**
     * Test-Konstruktor: erlaubt das Einschleusen eines vorkonfigurierten
     * {@link RestClient} (z.B. via {@code MockRestServiceServer}), ohne auf eine
     * {@code RestClient.Builder}-Bean angewiesen zu sein (die es hier nicht gibt).
     */
    PaperSearchService(RestClient http, String mailto) {
        this.http = http;
        this.mailto = mailto == null ? "" : mailto;
    }

    /** Ein Treffer der Trefferliste (das, was im Frontend zur Auswahl steht). */
    public record PaperHit(String ref, String title, String authors, Integer year, boolean openAccess) {
    }

    /** Aufgeloester, legaler Volltext-Link zu einem Treffer. */
    public record ResolvedPdf(String title, String pdfUrl) {
    }

    /**
     * Top-5-Treffer zu einem Thema (Relevanz-Sortierung von OpenAlex). Nicht-OA-
     * Treffer kommen mit {@code openAccess=false} zurueck und werden im Frontend
     * ausgegraut – ehrlicheres Bild als sie wegzulassen.
     */
    public List<PaperHit> search(String thema) {
        OpenAlexWorks resp = http.get()
                .uri(OPENALEX + "/works?search={s}&per_page=5&mailto={m}", thema, mailto)
                .retrieve()
                .body(OpenAlexWorks.class);
        if (resp == null || resp.results() == null) {
            return List.of();
        }
        return resp.results().stream().map(PaperSearchService::toHit).toList();
    }

    /**
     * Loest fuer einen OpenAlex-Treffer (z.B. {@code W2741809807}) den legalen
     * OA-PDF-Link auf: zuerst die beste OA-Location bei OpenAlex, sonst Unpaywall
     * ueber die DOI. {@link Optional#empty()} = kein freier Volltext gefunden.
     */
    public Optional<ResolvedPdf> resolvePdf(String ref) {
        OpenAlexWork work = http.get()
                .uri(OPENALEX + "/works/{id}?mailto={m}", ref, mailto)
                .retrieve()
                .body(OpenAlexWork.class);
        if (work == null) {
            return Optional.empty();
        }
        String title = work.displayName() != null ? work.displayName() : ref;
        String pdf = work.bestOaPdfUrl();
        if (pdf == null) {
            pdf = unpaywallPdf(work.doi());
        }
        return pdf == null ? Optional.empty() : Optional.of(new ResolvedPdf(title, pdf));
    }

    private String unpaywallPdf(String doiUrl) {
        if (doiUrl == null || mailto.isEmpty()) {
            return null; // Unpaywall verlangt zwingend eine E-Mail
        }
        String doi = doiUrl.replaceFirst("^https?://doi.org/", "");
        try {
            Unpaywall up = http.get()
                    .uri(UNPAYWALL + "/{doi}?email={m}", doi, mailto)
                    .retrieve()
                    .body(Unpaywall.class);
            return up != null && up.bestOaLocation() != null ? up.bestOaLocation().urlForPdf() : null;
        } catch (RuntimeException e) {
            return null; // DOI bei Unpaywall unbekannt o.ae. -> kein Volltext
        }
    }

    private static PaperHit toHit(OpenAlexWork w) {
        String ref = w.id() != null ? w.id().replaceFirst("^https?://openalex.org/", "") : "";
        String authors = w.authorString();
        boolean oa = w.openAccess() != null && Boolean.TRUE.equals(w.openAccess().isOa());
        return new PaperHit(ref, w.displayName(), authors, w.publicationYear(), oa);
    }

    // --- OpenAlex-/Unpaywall-JSON (nur die benoetigten Felder) ----------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenAlexWorks(List<OpenAlexWork> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenAlexWork(
            String id,
            String doi,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("publication_year") Integer publicationYear,
            @JsonProperty("open_access") OpenAccess openAccess,
            @JsonProperty("best_oa_location") OaLocation bestOaLocation,
            List<Authorship> authorships) {

        String bestOaPdfUrl() {
            if (bestOaLocation != null && bestOaLocation.pdfUrl() != null) {
                return bestOaLocation.pdfUrl();
            }
            return openAccess != null ? openAccess.oaUrl() : null;
        }

        String authorString() {
            if (authorships == null || authorships.isEmpty()) {
                return "";
            }
            String first = authorships.get(0).authorName();
            return authorships.size() > 1 ? first + " et al." : first;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenAccess(@JsonProperty("is_oa") Boolean isOa, @JsonProperty("oa_url") String oaUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OaLocation(@JsonProperty("pdf_url") String pdfUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Authorship(Author author) {
        String authorName() {
            return author != null ? author.displayName() : "";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Author(@JsonProperty("display_name") String displayName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Unpaywall(@JsonProperty("best_oa_location") UnpaywallLocation bestOaLocation) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UnpaywallLocation(@JsonProperty("url_for_pdf") String urlForPdf) {
    }
}
