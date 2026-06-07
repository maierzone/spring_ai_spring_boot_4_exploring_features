package com.example.springai.demo.feature16_downloads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import com.example.springai.demo.feature16_downloads.PaperSearchService.PaperHit;
import com.example.springai.demo.feature16_downloads.PaperSearchService.ResolvedPdf;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Offline-Tests der OpenAlex-/Unpaywall-Anbindung: Statt echter HTTP-Aufrufe
 * antwortet ein {@link MockRestServiceServer} mit festen JSON-Fixtures. Geprueft
 * wird das Mapping (Top-Treffer, OA-Flag, gekuerzte OpenAlex-Id, Autor-String)
 * sowie der Unpaywall-Fallback bei fehlendem OA-Link in OpenAlex.
 */
class PaperSearchServiceTest {

    private PaperSearchService service(MockServerHolder holder) {
        RestClient.Builder builder = RestClient.builder();
        holder.server = MockRestServiceServer.bindTo(builder).build();
        return new PaperSearchService(builder.build(), "test@example.com");
    }

    private static final class MockServerHolder {
        MockRestServiceServer server;
    }

    @Test
    void search_mapsTopHits_withOaFlagAndShortRefAndAuthors() {
        MockServerHolder h = new MockServerHolder();
        PaperSearchService service = service(h);
        String json = """
                {"results":[
                  {"id":"https://openalex.org/W111","display_name":"Animal Neurodivergence",
                   "publication_year":2021,
                   "open_access":{"is_oa":true,"oa_url":"https://example.org/a.pdf"},
                   "authorships":[{"author":{"display_name":"Jane Doe"}},
                                  {"author":{"display_name":"John Roe"}}]},
                  {"id":"https://openalex.org/W222","display_name":"Paywalled Study",
                   "publication_year":2019,
                   "open_access":{"is_oa":false,"oa_url":null},
                   "authorships":[{"author":{"display_name":"Max Mustermann"}}]}
                ]}""";
        h.server.expect(requestTo(Matchers.containsString("/works?search=")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<PaperHit> hits = service.search("Neurodivergenz bei Tieren");

        assertThat(hits).hasSize(2);
        PaperHit first = hits.get(0);
        assertThat(first.ref()).isEqualTo("W111");
        assertThat(first.title()).isEqualTo("Animal Neurodivergence");
        assertThat(first.year()).isEqualTo(2021);
        assertThat(first.openAccess()).isTrue();
        assertThat(first.authors()).isEqualTo("Jane Doe et al.");

        PaperHit second = hits.get(1);
        assertThat(second.ref()).isEqualTo("W222");
        assertThat(second.openAccess()).isFalse();
        assertThat(second.authors()).isEqualTo("Max Mustermann");
        h.server.verify();
    }

    @Test
    void resolvePdf_prefersBestOaLocationPdfUrl() {
        MockServerHolder h = new MockServerHolder();
        PaperSearchService service = service(h);
        String json = """
                {"id":"https://openalex.org/W111","display_name":"Animal Neurodivergence",
                 "doi":"https://doi.org/10.1/abc",
                 "open_access":{"is_oa":true,"oa_url":"https://example.org/landing"},
                 "best_oa_location":{"pdf_url":"https://example.org/direct.pdf"}}""";
        h.server.expect(requestTo(Matchers.containsString("/works/W111")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        ResolvedPdf pdf = service.resolvePdf("W111").orElseThrow();

        assertThat(pdf.title()).isEqualTo("Animal Neurodivergence");
        assertThat(pdf.pdfUrl()).isEqualTo("https://example.org/direct.pdf");
        h.server.verify();
    }

    @Test
    void resolvePdf_empty_whenNoOpenAccessAnywhere() {
        MockServerHolder h = new MockServerHolder();
        PaperSearchService service = service(h);
        String json = """
                {"id":"https://openalex.org/W333","display_name":"No Fulltext",
                 "open_access":{"is_oa":false,"oa_url":null}}""";
        h.server.expect(requestTo(Matchers.containsString("/works/W333")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThat(service.resolvePdf("W333")).isEmpty();
        h.server.verify();
    }
}
