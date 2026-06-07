package com.example.springai.demo.feature17_ebooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import com.example.springai.demo.feature17_ebooks.EbookSearchService.EbookHit;
import com.example.springai.demo.feature17_ebooks.EbookSearchService.ResolvedFile;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Offline-Tests der Gutendex-/Open-Library-/Archive-Anbindung: Statt echter
 * HTTP-Aufrufe antwortet ein {@link MockRestServiceServer} mit festen
 * JSON-Fixtures. Geprueft wird das Mapping beider Quellen (Treffer, bestes
 * Format, Download-Flag) sowie die Aufloesung des Download-Links pro Quelle.
 */
class EbookSearchServiceTest {

    private EbookSearchService service(MockServerHolder holder) {
        RestClient.Builder builder = RestClient.builder();
        holder.server = MockRestServiceServer.bindTo(builder).build();
        return new EbookSearchService(builder.build());
    }

    private static final class MockServerHolder {
        MockRestServiceServer server;
    }

    @Test
    void searchGutendex_picksBestFormat_andMarksDownloadable() {
        MockServerHolder h = new MockServerHolder();
        EbookSearchService service = service(h);
        String json = """
                {"results":[
                  {"id":1234,"title":"Faust","authors":[{"name":"Goethe, Johann Wolfgang von"}],
                   "formats":{"text/html":"https://x/h.htm",
                              "application/epub+zip":"https://x/f.epub",
                              "text/plain; charset=utf-8":"https://x/f.txt"}}
                ]}""";
        h.server.expect(requestTo(Matchers.containsString("gutendex.com/books?search=")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        // Open-Library-Aufruf der kombinierten Suche: leere Trefferliste.
        h.server.expect(requestTo(Matchers.containsString("openlibrary.org/search.json")))
                .andRespond(withSuccess("{\"docs\":[]}", MediaType.APPLICATION_JSON));

        List<EbookHit> hits = service.search("Faust");

        assertThat(hits).hasSize(1);
        EbookHit hit = hits.get(0);
        assertThat(hit.ref()).isEqualTo("gutendex:1234");
        assertThat(hit.title()).isEqualTo("Faust");
        assertThat(hit.authors()).isEqualTo("Goethe, Johann Wolfgang von");
        assertThat(hit.format()).isEqualTo("EPUB"); // PDF fehlt -> EPUB vor TXT
        assertThat(hit.downloadable()).isTrue();
        h.server.verify();
    }

    @Test
    void searchOpenLibrary_borrowableOnly_isNotDownloadable() {
        MockServerHolder h = new MockServerHolder();
        EbookSearchService service = service(h);
        h.server.expect(requestTo(Matchers.containsString("gutendex.com/books?search=")))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));
        String json = """
                {"docs":[
                  {"key":"/works/OL1W","title":"Public Classic","author_name":["Some Author"],
                   "first_publish_year":1890,"ia":["publicclassic00"],"ebook_access":"public"},
                  {"key":"/works/OL2W","title":"Lending Only","author_name":["Modern Author"],
                   "first_publish_year":2015,"ia":["lending00"],"ebook_access":"borrowable"}
                ]}""";
        h.server.expect(requestTo(Matchers.containsString("openlibrary.org/search.json")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<EbookHit> hits = service.search("classic");

        assertThat(hits).hasSize(2);
        EbookHit pub = hits.get(0);
        assertThat(pub.ref()).isEqualTo("openlibrary:publicclassic00");
        assertThat(pub.downloadable()).isTrue();
        assertThat(pub.year()).isEqualTo(1890);

        EbookHit lend = hits.get(1);
        assertThat(lend.downloadable()).isFalse();
        assertThat(lend.format()).isEqualTo("nur Verweis");
        h.server.verify();
    }

    @Test
    void resolveGutendex_prefersPdfOverEpub() {
        MockServerHolder h = new MockServerHolder();
        EbookSearchService service = service(h);
        String json = """
                {"id":1234,"title":"Faust",
                 "formats":{"application/epub+zip":"https://x/f.epub",
                            "application/pdf":"https://x/f.pdf"}}""";
        h.server.expect(requestTo(Matchers.containsString("gutendex.com/books/1234")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        ResolvedFile file = service.resolve("gutendex:1234").orElseThrow();

        assertThat(file.title()).isEqualTo("Faust");
        assertThat(file.url()).isEqualTo("https://x/f.pdf");
        assertThat(file.contentType()).isEqualTo("application/pdf");
        h.server.verify();
    }

    @Test
    void resolveArchive_buildsDownloadUrl_fromBestFile() {
        MockServerHolder h = new MockServerHolder();
        EbookSearchService service = service(h);
        String json = """
                {"metadata":{"title":"Public Classic"},
                 "files":[{"name":"publicclassic00_djvu.txt","format":"DjVuTXT"},
                          {"name":"publicclassic00.pdf","format":"Text PDF"}]}""";
        h.server.expect(requestTo(Matchers.containsString("archive.org/metadata/publicclassic00")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        ResolvedFile file = service.resolve("openlibrary:publicclassic00").orElseThrow();

        assertThat(file.title()).isEqualTo("Public Classic");
        assertThat(file.url()).isEqualTo("https://archive.org/download/publicclassic00/publicclassic00.pdf");
        assertThat(file.contentType()).isEqualTo("application/pdf");
        h.server.verify();
    }

    @Test
    void resolve_unknownRefPrefix_isEmpty() {
        MockServerHolder h = new MockServerHolder();
        EbookSearchService service = service(h);
        assertThat(service.resolve("olref:/works/OL9W")).isEmpty();
    }
}
