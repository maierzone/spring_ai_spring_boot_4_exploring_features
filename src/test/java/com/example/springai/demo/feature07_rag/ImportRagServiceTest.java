package com.example.springai.demo.feature07_rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.example.springai.demo.embedding.HashingEmbeddingModel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;

/**
 * Offline-Test der Laufzeit-Import-Logik: Dateien aus einem Ordner werden in den
 * (vom Telematik-Wissen getrennten) Import-Store gelegt, die Ähnlichkeitssuche
 * findet anschliessend nur diese Dokumente. Läuft ohne LLM/Netz.
 */
class ImportRagServiceTest {

    private ImportRagService newService() {
        return new ImportRagService(new HashingEmbeddingModel());
    }

    @Test
    void importFolderLiestTextdateienUndDurchsuchtNurDiese(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("notiz.md"),
                "Der Apfelbaum braucht im Sommer regelmaessig Wasser.\n\n"
                        + "Birnen reifen meist erst im Spaetsommer.",
                StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("ignorieren.json"), "{\"kein\":\"import\"}", StandardCharsets.UTF_8);

        ImportRagService service = newService();
        int documents = service.importFolder(dir);

        // Zwei Absätze aus der .md, die .json wird ignoriert.
        assertThat(documents).isEqualTo(2);
        assertThat(service.documentCount()).isEqualTo(2);
        assertThat(service.sources()).containsExactly("notiz.md");

        List<Document> hits = service.store().similaritySearch(
                SearchRequest.builder().query("Wann reifen Birnen?").topK(1).build());
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getText()).contains("Birnen");
        assertThat(hits.get(0).getMetadata()).containsEntry("source", "notiz.md");
    }

    @Test
    void offlineDefaultMeldetNichtSemantischesHashing() {
        assertThat(newService().embeddingLabel()).contains("Hashing");
    }

    @Test
    void uploadFuegtHinzu_clearLeert() {
        ImportRagService service = newService();

        int added = service.addUpload("Ein einzelner Absatz als Upload.".getBytes(StandardCharsets.UTF_8), "upload.txt");
        assertThat(added).isEqualTo(1);
        assertThat(service.documentCount()).isEqualTo(1);
        assertThat(service.sources()).containsExactly("upload.txt");

        service.clear();
        assertThat(service.documentCount()).isZero();
        assertThat(service.sources()).isEmpty();
    }

    @Test
    void importFolderErsetztBisherigenBestand(@TempDir Path dir) throws Exception {
        ImportRagService service = newService();
        service.addUpload("Vorab hochgeladener Inhalt.".getBytes(StandardCharsets.UTF_8), "vorab.txt");

        Files.writeString(dir.resolve("neu.txt"), "Frischer Ordnerinhalt.", StandardCharsets.UTF_8);
        service.importFolder(dir);

        // Der Ordner-Scan ersetzt den Bestand (Re-Index), der Upload ist weg.
        assertThat(service.sources()).containsExactly("neu.txt");
        assertThat(service.documentCount()).isEqualTo(1);
    }

    @Test
    void nichtUnterstuetzterUploadWirdAbgewiesen() {
        ImportRagService service = newService();
        assertThatThrownBy(() -> service.addUpload(new byte[] {1, 2, 3}, "bild.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nicht unterstützter Dateityp");
    }
}
