package com.gitobserver.report;

import com.gitobserver.model.ActivitySnapshot;
import com.gitobserver.model.AuthorCount;
import com.gitobserver.model.CommitRecord;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityPdfTest {
    @Test
    void writesTheFilteredCommits(@TempDir Path folder) throws Exception {
        List<CommitRecord> commits = new ArrayList<>();
        commits.add(new CommitRecord(
                "Trabajo",
                "ana/app",
                "abc1234def",
                "https://github.com/ana/app/commit/abc1234def",
                "José",
                "Añadió el informe\nDetalle interno",
                Instant.parse("2026-10-01T18:30:00Z")
        ));
        for (int index = 0; index < 45; index++) {
            commits.add(new CommitRecord(
                    "Trabajo",
                    "ana/app",
                    "sha" + index + "xxxxx",
                    "",
                    "persona-" + index,
                    "cambio " + index,
                    Instant.parse("2026-10-02T09:00:00Z")
            ));
        }
        ActivitySnapshot snapshot = new ActivitySnapshot(
                Instant.parse("2026-10-05T13:00:00Z"),
                "2026-09-28 00:00",
                "2026-10-05 08:00",
                "ana/app",
                "José",
                "Trabajo",
                true,
                false,
                0,
                List.of(new AuthorCount("José", 1), new AuthorCount("persona-44", 1)),
                commits
        );
        Path file = folder.resolve("actividad.pdf");
        ActivityPdf.write(file, snapshot);

        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            assertTrue(document.getNumberOfPages() >= 2);
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Reporte de actividad"));
            assertTrue(text.contains("José"));
            assertTrue(text.contains("Añadió el informe"));
            assertTrue(text.contains("abc1234"));
            assertTrue(text.contains("ana/app"));
            assertTrue(text.contains("Sí"));
            assertFalse(text.contains("Detalle interno"));
        }
    }

    @Test
    void keepsOnlyTheFirstLineOfTheMessage() {
        assertEquals("Añadió el informe", ActivityPdf.subject("Añadió el informe\nDetalle interno"));
    }
}
