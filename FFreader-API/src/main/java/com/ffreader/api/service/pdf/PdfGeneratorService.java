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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private static final int MAX_FILE_TITLE_LENGTH = 100;
    private static final int MAX_FILE_TITLE_BYTES = 200;

    private static final String FONT_REGULAR = "/fonts/DejaVuSans.ttf";
    private static final String FONT_BOLD = "/fonts/DejaVuSans-Bold.ttf";
    private static final String FONT_CHINESE_JAPANESE = "/fonts/NotoSansSC-Regular.ttf";
    private static final String FONT_KOREAN = "/fonts/NotoSansKR-Regular.ttf";

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
        Path outputPath = pdfDir.resolve("%s %s.pdf".formatted(toFileTitle(fanfic.getTitle()), fanfic.getId()));
        try (PDDocument document = new PDDocument()) {
            PdfFontChain.LazyFont chineseJapanese = new PdfFontChain.LazyFont(document, FONT_CHINESE_JAPANESE);
            PdfFontChain.LazyFont korean = new PdfFontChain.LazyFont(document, FONT_KOREAN);
            PdfFontChain regularFont = new PdfFontChain(List.of(
                    new PdfFontChain.LazyFont(document, FONT_REGULAR), chineseJapanese, korean));
            PdfFontChain boldFont = new PdfFontChain(List.of(
                    new PdfFontChain.LazyFont(document, FONT_BOLD), chineseJapanese, korean));

            PageCursor cursor = new PageCursor(document, regularFont);
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

    /** Story titles can contain characters that aren't allowed in file names, such as ':' on Windows. */
    private String toFileTitle(String title) {
        if (title == null || title.isBlank()) {
            return "story";
        }
        // Lone surrogates can't be encoded in a Linux file name.
        String safe = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}\\p{Cs}]+", "_").strip();
        return truncate(safe).replaceAll("[. ]+$", "");
    }

    /**
     * Cuts between whole characters, within the limits of both Windows (255 UTF-16 units)
     * and Linux (255 bytes), leaving room for the " {uuid}.pdf" suffix.
     */
    private String truncate(String text) {
        StringBuilder result = new StringBuilder();
        int length = 0;
        int bytes = 0;
        for (int codePoint : text.codePoints().toArray()) {
            length++;
            bytes += Character.toString(codePoint).getBytes(StandardCharsets.UTF_8).length;
            if (length > MAX_FILE_TITLE_LENGTH || bytes > MAX_FILE_TITLE_BYTES) {
                break;
            }
            result.appendCodePoint(codePoint);
        }
        return result.toString();
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
        private final PdfFontChain regularFont;
        private PDPageContentStream stream;
        private float y;

        PageCursor(PDDocument document, PdfFontChain regularFont) {
            this.document = document;
            this.regularFont = regularFont;
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

        void writeParagraph(String text, float fontSize, PdfFontChain font, float leading) throws IOException {
            if (text == null || text.isBlank()) {
                return;
            }
            for (String line : wrap(text, font, fontSize, USABLE_WIDTH)) {
                ensureSpace(leading);
                stream.beginText();
                stream.newLineAtOffset(MARGIN, y);
                for (PdfFontChain.Run run : font.toRuns(line)) {
                    stream.setFont(run.font(), fontSize);
                    stream.showText(run.text());
                }
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

        /** Wraps on spaces, and inside words too long for a line, which covers CJK text without spaces. */
        private List<String> wrap(String text, PdfFontChain font, float fontSize, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            float lineWidth = 0;
            float spaceWidth = font.width(' ', fontSize);

            for (String word : text.strip().split("\\s+")) {
                float wordWidth = font.width(word, fontSize);
                if (!line.isEmpty() && lineWidth + spaceWidth + wordWidth <= maxWidth) {
                    line.append(' ').append(word);
                    lineWidth += spaceWidth + wordWidth;
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                    lineWidth = 0;
                }
                if (wordWidth <= maxWidth) {
                    line.append(word);
                    lineWidth = wordWidth;
                    continue;
                }
                for (int codePoint : word.codePoints().toArray()) {
                    float charWidth = font.width(codePoint, fontSize);
                    if (lineWidth + charWidth > maxWidth && !line.isEmpty()) {
                        lines.add(line.toString());
                        line.setLength(0);
                        lineWidth = 0;
                    }
                    line.appendCodePoint(codePoint);
                    lineWidth += charWidth;
                }
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
            return lines;
        }
    }
}
