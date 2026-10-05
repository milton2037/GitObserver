package com.gitobserver.report;

import com.gitobserver.model.AuthorCount;
import com.gitobserver.model.BucketCount;
import com.gitobserver.model.RepoActivity;
import com.gitobserver.model.StatsSnapshot;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsPdfTest {
    @Test
    void writesTheFilteredStatistics(@TempDir Path folder) throws Exception {
        List<AuthorCount> authors = new ArrayList<>();
        authors.add(new AuthorCount("José", 4));
        for (int index = 0; index < 40; index++) {
            authors.add(new AuthorCount("persona-" + index, 1));
        }
        StatsSnapshot snapshot = new StatsSnapshot(
                Instant.parse("2026-10-05T13:00:00Z"),
                "Últimos 7 días",
                "2026-09-28 00:00",
                "2026-10-05 08:00",
                "ana/app",
                "Trabajo",
                true,
                false,
                0,
                44,
                authors.size(),
                1,
                "18:00",
                authors,
                List.of(new BucketCount("2026-10-01", 4)),
                List.of(new BucketCount("18:00", 4)),
                List.of(new RepoActivity("ana/app", 44, 2))
        );
        Path file = folder.resolve("reporte.pdf");
        StatsPdf.write(file, snapshot);

        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            assertTrue(document.getNumberOfPages() >= 2);
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Reporte de estadísticas"));
            assertTrue(text.contains("ana/app"));
            assertTrue(text.contains("José"));
            assertTrue(text.contains("Últimos 7 días"));
            assertTrue(text.contains("persona-39"));
            assertTrue(text.contains("Sí"));
        }
    }
}
