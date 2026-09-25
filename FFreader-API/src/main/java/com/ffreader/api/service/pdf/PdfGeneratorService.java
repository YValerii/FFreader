package com.ffreader.api.service.pdf;

import com.ffreader.api.config.FfreaderProperties;
import com.ffreader.api.domain.Chapter;
import com.ffreader.api.domain.Fanfic;
import com.ffreader.api.exception.FanficScrapeException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a fanfic's title, description and all chapters into one clean,
 * easy-to-read PDF (HTML tags stripped, plain wrapped text).
 */
@Slf4j
@Service
public class PdfGeneratorService {

    private static final float MARGIN = 50f;
    private static final float PAGE_WIDTH = PDRectangle.LETTER.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.LETTER.getHeight();
    private static final float USABLE_WIDTH = PAGE_WIDTH - 2 * MARGIN;

    private static final float TITLE_SIZE = 20f;
    private static final float HEADING_SIZE = 15f;
    private static final float BODY_SIZE = 11f;
    private static final float BODY_LEADING = 15f;

    private final HtmlToTextConverter htmlToTextConverter;
    private final Path pdfDir;

    public PdfGeneratorService(HtmlToTextConverter htmlToTextConverter, FfreaderProperties properties) {
        this.htmlToTextConverter = htmlToTextConverter;
        String configured = properties.storage() != null ? properties.storage().pdfDir() : null;
        this.pdfDir = Path.of(configured != null ? configured : "/tmp/ffreader/pdfs");
        try {
            Files.createDirectories(pdfDir);
        } catch (IOException e) {
            throw new FanficScrapeException("Could not create pdf directory: " + pdfDir, e);
        }
    }

    /**
     * @param fanfic   the fanfic whose title/description page is rendered first
     * @param chapters ordered chapters (raw HTML) to render below it
     * @return the absolute path of the generated PDF file
     */
    public String generate(Fanfic fanfic, List<Chapter> chapters) {
        Path outputPath = pdfDir.resolve(fanfic.getId() + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDFont regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            PageCursor cursor = new PageCursor(document, regularFont, boldFont);
            cursor.newPage();

            cursor.writeParagraph(fanfic.getTitle(), TITLE_SIZE, boldFont, TITLE_SIZE + 6);
            cursor.writeMetaLine(buildMetaLine(fanfic));
            cursor.writeSpacer(10);
            cursor.writeParagraph(fanfic.getDescription(), BODY_SIZE, regularFont, BODY_LEADING);

            for (Chapter chapter : chapters) {
                cursor.newPage();
                String heading = "Chapter " + chapter.getChapterNumber()
                        + (chapter.getTitle() != null && !chapter.getTitle().isBlank() ? ": " + chapter.getTitle() : "");
                cursor.writeParagraph(heading, HEADING_SIZE, boldFont, HEADING_SIZE + 6);
                cursor.writeSpacer(8);

                String plainText = htmlToTextConverter.toPlainText(chapter.getRawHtml());
                for (String paragraph : plainText.split("\n\n")) {
                    cursor.writeParagraph(paragraph.replace("\n", " ").strip(), BODY_SIZE, regularFont, BODY_LEADING);
                    cursor.writeSpacer(6);
                }
            }

            cursor.close();
            document.save(outputPath.toFile());
        } catch (IOException e) {
            throw new FanficScrapeException("Failed to generate PDF for fanfic " + fanfic.getId(), e);
        }
        log.info("Generated PDF at {}", outputPath);
        return outputPath.toAbsolutePath().toString();
    }

    private String buildMetaLine(Fanfic fanfic) {
        List<String> parts = new ArrayList<>();
        if (fanfic.getRating() != null) parts.add("Rated: " + fanfic.getRating());
        if (fanfic.getLanguage() != null) parts.add(fanfic.getLanguage());
        if (fanfic.getGenre() != null) parts.add(fanfic.getGenre());
        if (fanfic.getCharacters() != null) parts.add(fanfic.getCharacters());
        if (fanfic.getChaptersCount() != null) parts.add("Chapters: " + fanfic.getChaptersCount());
        if (fanfic.getWords() != null) parts.add("Words: " + fanfic.getWords());
        return String.join("  |  ", parts);
    }

    /**
     * Small stateful helper that tracks the current page/content-stream and
     * y-cursor, opening new pages automatically as content overflows.
     */
    private static class PageCursor {
        private final PDDocument document;
        private final PDFont regularFont;
        private final PDFont boldFont;
        private PDPageContentStream stream;
        private float y;

        PageCursor(PDDocument document, PDFont regularFont, PDFont boldFont) {
            this.document = document;
            this.regularFont = regularFont;
            this.boldFont = boldFont;
        }

        void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE_HEIGHT - MARGIN;
        }

        void writeSpacer(float height) throws IOException {
            y -= height;
            ensureSpace(0);
        }

        void writeMetaLine(String text) throws IOException {
            writeParagraph(text, 9f, regularFont, 12f);
        }

        void writeParagraph(String text, float fontSize, PDFont font, float leading) throws IOException {
            if (text == null || text.isBlank()) {
                return;
            }
            for (String line : wrap(text, font, fontSize, USABLE_WIDTH)) {
                ensureSpace(leading);
                stream.beginText();
                stream.setFont(font, fontSize);
                stream.newLineAtOffset(MARGIN, y);
                stream.showText(sanitizeForPdf(line));
                stream.endText();
                y -= leading;
            }
        }

        private void ensureSpace(float needed) throws IOException {
            if (y - needed < MARGIN) {
                newPage();
            }
        }

        void close() throws IOException {
            if (stream != null) {
                stream.close();
            }
        }

        private List<String> wrap(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            for (String rawLine : text.split("\n")) {
                StringBuilder current = new StringBuilder();
                for (String word : rawLine.split(" ")) {
                    String candidate = current.isEmpty() ? word : current + " " + word;
                    if (width(candidate, font, fontSize) > maxWidth && !current.isEmpty()) {
                        lines.add(current.toString());
                        current = new StringBuilder(word);
                    } else {
                        current = new StringBuilder(candidate);
                    }
                }
                lines.add(current.toString());
            }
            return lines;
        }

        private float width(String text, PDFont font, float fontSize) throws IOException {
            return font.getStringWidth(sanitizeForPdf(text)) / 1000 * fontSize;
        }

        /** Standard14 fonts only support WinAnsi-ish characters; drop anything else safely. */
        private String sanitizeForPdf(String text) {
            StringBuilder sb = new StringBuilder(text.length());
            for (char c : text.toCharArray()) {
                sb.append(c < 256 ? c : '?');
            }
            return sb.toString();
        }
    }
}
