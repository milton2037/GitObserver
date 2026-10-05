package com.gitobserver.report;

import com.gitobserver.model.ActivitySnapshot;
import com.gitobserver.model.AuthorCount;
import com.gitobserver.model.CommitRecord;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ActivityPdf {
    private static final float MARGIN = 36;
    private static final float BOTTOM = 42;
    private static final float ROW = 16;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final PDDocument document = new PDDocument();
    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private PDPage page;
    private PDPageContentStream stream;
    private float y;
    private int pageNumber;

    private ActivityPdf() {
    }

    public static void write(Path path, ActivitySnapshot snapshot) throws IOException {
        if (path == null || snapshot == null) {
            throw new IllegalArgumentException("Indica el archivo y los datos del reporte.");
        }
        ActivityPdf pdf = new ActivityPdf();
        try {
            pdf.render(snapshot);
            pdf.finish();
            pdf.document.save(path.toFile());
        } finally {
            if (pdf.stream != null) {
                pdf.stream.close();
            }
            pdf.document.close();
        }
    }

    private void render(ActivitySnapshot snapshot) throws IOException {
        newPage();
        title("GitObserver");
        text(bold, 16, "Reporte de actividad");
        y -= 4;
        muted("Generado: " + WHEN.format(snapshot.generatedAt() == null ? java.time.Instant.now() : snapshot.generatedAt()));
        if (snapshot.cancelled()) {
            muted("La consulta se canceló. El reporte incluye solo los commits que se alcanzaron a leer.");
        }
        if (snapshot.warnings() > 0) {
            muted("Avisos al consultar GitHub: " + snapshot.warnings() + ".");
        }
        y -= 6;
        section("Filtros");
        line("Desde", snapshot.from());
        line("Hasta", snapshot.to());
        line("Repositorio", snapshot.repository());
        line("Colaborador", snapshot.author());
        line("Cuentas", snapshot.accounts());
        line("Ocultar bots", snapshot.hideBots() ? "Sí" : "No");
        y -= 8;
        section("Resumen");
        long repos = snapshot.commits().stream().map(CommitRecord::repository).distinct().count();
        kpis(snapshot.commits().size(), snapshot.authors().size(), (int) repos);
        y -= 6;
        section("Colaboradores");
        rankHeader();
        if (snapshot.authors().isEmpty()) {
            emptyRow("Sin commits en el rango.");
        } else {
            int max = snapshot.authors().stream().mapToInt(AuthorCount::commits).max().orElse(1);
            for (AuthorCount author : snapshot.authors()) {
                rankRow(author.author(), author.commits(), max);
            }
        }
        y -= 10;
        section("Commits");
        commitHeader();
        if (snapshot.commits().isEmpty()) {
            emptyRow("Sin commits en el rango.");
        } else {
            for (CommitRecord commit : snapshot.commits()) {
                commitRow(commit);
            }
        }
    }

    private void kpis(int commits, int people, int repositories) throws IOException {
        float width = (contentWidth() - 12) / 3f;
        float x = MARGIN;
        String[] captions = {"Commits", "Colaboradores", "Repositorios"};
        String[] values = {Integer.toString(commits), Integer.toString(people), Integer.toString(repositories)};
        ensure(48);
        for (int index = 0; index < captions.length; index++) {
            stream.setNonStrokingColor(0.95f, 0.96f, 0.98f);
            stream.addRect(x, y - 36, width, 42);
            stream.fill();
            stream.setNonStrokingColor(0.39f, 0.45f, 0.55f);
            textAt(x + 8, y - 2, regular, 9, captions[index]);
            stream.setNonStrokingColor(0.12f, 0.16f, 0.22f);
            textAt(x + 8, y - 22, bold, 14, values[index]);
            x += width + 6;
        }
        y -= 52;
    }

    private void title(String value) throws IOException {
        stream.setNonStrokingColor(0.31f, 0.27f, 0.89f);
        stream.addRect(0, page.getMediaBox().getHeight() - 28, page.getMediaBox().getWidth(), 28);
        stream.fill();
        stream.setNonStrokingColor(1, 1, 1);
        textAt(MARGIN, page.getMediaBox().getHeight() - 18, bold, 12, value);
        y = page.getMediaBox().getHeight() - 50;
        stream.setNonStrokingColor(0.06f, 0.09f, 0.16f);
    }

    private void section(String value) throws IOException {
        ensure(34);
        y -= 6;
        stream.setNonStrokingColor(0.12f, 0.16f, 0.22f);
        text(bold, 13, value);
        stream.setStrokingColor(0.89f, 0.91f, 0.94f);
        stream.setLineWidth(1);
        stream.moveTo(MARGIN, y + 4);
        stream.lineTo(MARGIN + contentWidth(), y + 4);
        stream.stroke();
        y -= 6;
    }

    private void line(String label, String value) throws IOException {
        ensure(ROW);
        stream.setNonStrokingColor(0.39f, 0.45f, 0.55f);
        textAt(MARGIN, y, regular, 10, fit(regular, 10, label, 110));
        stream.setNonStrokingColor(0.06f, 0.09f, 0.16f);
        textAt(MARGIN + 118, y, regular, 10, fit(regular, 10, blank(value), contentWidth() - 118));
        y -= ROW;
    }

    private void muted(String value) throws IOException {
        ensure(16);
        stream.setNonStrokingColor(0.39f, 0.45f, 0.55f);
        text(regular, 10, value);
        stream.setNonStrokingColor(0.06f, 0.09f, 0.16f);
    }

    private void rankHeader() throws IOException {
        headerBand();
        stream.setNonStrokingColor(0.28f, 0.33f, 0.41f);
        textAt(MARGIN + 6, y, bold, 9, "Colaborador");
        textAt(MARGIN + contentWidth() - 70, y, bold, 9, "Commits");
        y -= ROW;
    }

    private void rankRow(String name, int commits, int max) throws IOException {
        ensure(ROW);
        stream.setNonStrokingColor(0.06f, 0.09f, 0.16f);
        textAt(MARGIN + 6, y, regular, 10, fit(regular, 10, blank(name), contentWidth() - 170));
        float bar = max <= 0 ? 0 : 70f * commits / max;
        stream.setNonStrokingColor(0.39f, 0.40f, 0.95f);
        stream.addRect(MARGIN + contentWidth() - 156, y - 2, Math.max(2, bar), 8);
        stream.fill();
        stream.setNonStrokingColor(0.06f, 0.09f, 0.16f);
        textAt(MARGIN + contentWidth() - 70, y, regular, 10, Integer.toString(commits));
        y -= ROW;
    }

    private void commitHeader() throws IOException {
        headerBand();
        stream.setNonStrokingColor(0.28f, 0.33f, 0.41f);
        float x = MARGIN + 4;
        textAt(x, y, bold, 9, "Fecha");
        textAt(x + 108, y, bold, 9, "Colaborador");
        textAt(x + 230, y, bold, 9, "Repositorio");
        textAt(x + 390, y, bold, 9, "Commit");
        textAt(x + 450, y, bold, 9, "Mensaje");
        y -= ROW;
    }

    private void commitRow(CommitRecord commit) throws IOException {
        ensure(ROW);
        stream.setNonStrokingColor(0.06f, 0.09f, 0.16f);
        float x = MARGIN + 4;
        String when = commit.committedAt() == null ? "-" : WHEN.format(commit.committedAt());
        textAt(x, y, regular, 9, fit(regular, 9, when, 102));
        textAt(x + 108, y, regular, 9, fit(regular, 9, blank(commit.author()), 116));
        textAt(x + 230, y, regular, 9, fit(regular, 9, blank(commit.repository()), 154));
        textAt(x + 390, y, regular, 9, fit(regular, 9, shortSha(commit.sha()), 54));
        textAt(x + 450, y, regular, 9, fit(regular, 9, subject(commit.message()), contentWidth() - 454));
        y -= ROW;
    }

    private void emptyRow(String message) throws IOException {
        ensure(ROW);
        stream.setNonStrokingColor(0.39f, 0.45f, 0.55f);
        textAt(MARGIN + 6, y, regular, 10, message);
        y -= ROW;
        stream.setNonStrokingColor(0.06f, 0.09f, 0.16f);
    }

    private void headerBand() throws IOException {
        ensure(ROW + 4);
        stream.setNonStrokingColor(0.95f, 0.96f, 0.98f);
        stream.addRect(MARGIN, y - 5, contentWidth(), ROW);
        stream.fill();
    }

    private void text(PDFont font, float size, String value) throws IOException {
        textAt(MARGIN, y, font, size, value);
        y -= size + 6;
    }

    private void textAt(float x, float baseline, PDFont font, float size, String value) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, baseline);
        stream.showText(pdf(font, value));
        stream.endText();
    }

    private void ensure(float needed) throws IOException {
        if (y - needed < BOTTOM) {
            newPage();
        }
    }

    private void finish() throws IOException {
        if (stream != null) {
            footer();
            stream.close();
            stream = null;
        }
    }

    private void newPage() throws IOException {
        if (stream != null) {
            footer();
            stream.close();
        }
        page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
        document.addPage(page);
        stream = new PDPageContentStream(document, page);
        pageNumber++;
        y = page.getMediaBox().getHeight() - MARGIN;
    }

    private void footer() throws IOException {
        stream.setNonStrokingColor(0.58f, 0.64f, 0.71f);
        stream.beginText();
        stream.setFont(regular, 9);
        stream.newLineAtOffset(MARGIN, 22);
        stream.showText(pdf(regular, "GitObserver  ·  página " + pageNumber));
        stream.endText();
    }

    private float contentWidth() {
        return page.getMediaBox().getWidth() - MARGIN * 2;
    }

    private String fit(PDFont font, float size, String value, float width) throws IOException {
        String text = pdf(font, blank(value));
        if (stringWidth(font, size, text) <= width) {
            return text;
        }
        String ellipsis = "...";
        while (text.length() > 1 && stringWidth(font, size, text + ellipsis) > width) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }

    private static float stringWidth(PDFont font, float size, String value) throws IOException {
        return font.getStringWidth(value) / 1000f * size;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    static String subject(String message) {
        if (message == null || message.isBlank()) {
            return "-";
        }
        int breakAt = message.indexOf('\n');
        String first = breakAt < 0 ? message : message.substring(0, breakAt);
        return first.strip();
    }

    private static String shortSha(String sha) {
        if (sha == null || sha.isBlank()) {
            return "-";
        }
        return sha.length() <= 7 ? sha : sha.substring(0, 7);
    }

    static String pdf(PDFont font, String value) throws IOException {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String source = value.replace('—', '-').replace('–', '-').replace('\n', ' ').replace('\r', ' ');
        StringBuilder safe = new StringBuilder();
        for (int index = 0; index < source.length(); ) {
            int point = source.codePointAt(index);
            String character = new String(Character.toChars(point));
            try {
                font.encode(character);
                safe.append(character);
            } catch (IllegalArgumentException | IOException ex) {
                safe.append('?');
            }
            index += Character.charCount(point);
        }
        return safe.toString();
    }
}
