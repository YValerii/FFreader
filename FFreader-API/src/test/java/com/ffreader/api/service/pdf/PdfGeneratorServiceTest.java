package com.ffreader.api.service.pdf;

import com.ffreader.api.config.FfreaderProperties;
import com.ffreader.api.domain.Chapter;
import com.ffreader.api.domain.Fanfic;
import com.ffreader.api.exception.FanficScrapeException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfGeneratorServiceTest {

    @TempDir
    Path pdfDir;

    private PdfGeneratorService service;

    @BeforeEach
    void setUp() {
        FfreaderProperties properties = new FfreaderProperties(
                null, null, new FfreaderProperties.Storage(pdfDir.toString(), pdfDir.toString()));
        service = new PdfGeneratorService(new HtmlToTextConverter(), properties);
    }

    @Test
    void keepsTextFromAllSupportedScripts() throws IOException {
        String text = generateAndExtract("Заголовок",
                "<p>Привет, мир. Ελληνικά. café “quoted” — it’s</p>"
                        + "<p>你好世界</p><p>こんにちは</p><p>안녕하세요</p>");

        assertThat(text).contains(
                "Заголовок", "Привет, мир", "Ελληνικά", "café", "“quoted”", "it’s",
                "你好世界", "こんにちは", "안녕하세요");
    }

    @Test
    void replacesCharactersNoFontCovers() throws IOException {
        // U+1F9FF is an emoji none of the bundled fonts have.
        String text = generateAndExtract("Title", "<p>before 🧿 after</p>");

        assertThat(text).contains("before ? after");
    }

    @Test
    void dropsInvisibleFormattingCharacters() throws IOException {
        String text = generateAndExtract("Title", "<p>zero​width</p>");

        assertThat(text).contains("zerowidth");
    }

    @Test
    void wrapsLongTextWithoutSpacesInsideThePage() throws IOException {
        String longText = "你好世界".repeat(2000);

        File pdf = generate("Title", "<p>" + longText + "</p>");

        try (PDDocument document = Loader.loadPDF(pdf)) {
            float[] maxX = {0};
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String string, List<TextPosition> positions) throws IOException {
                    for (TextPosition position : positions) {
                        maxX[0] = Math.max(maxX[0], position.getXDirAdj() + position.getWidthDirAdj());
                    }
                    super.writeString(string, positions);
                }
            };
            String text = stripper.getText(document).replaceAll("\\s+", "");

            assertThat(text).contains(longText);
            assertThat(document.getNumberOfPages()).isGreaterThan(2);
            assertThat(maxX[0]).isLessThanOrEqualTo(document.getPage(0).getMediaBox().getWidth() - 49);
        }
    }

    @Test
    void acceptsTitlesWithCharactersIllegalInFileNames() throws IOException {
        File pdf = generate("Part 1: What/Why? \"Now\" <here>...", "<p>body</p>");

        assertThat(pdf).exists();
        assertThat(pdf.getName()).startsWith("Part 1_ What_Why_ _Now_ _here_");
    }

    @Test
    void limitsFileNameLengthForLongTitles() throws IOException {
        File pdf = generate("x".repeat(1000), "<p>body</p>");

        assertThat(pdf).exists();
        assertThat(pdf.getName().length()).isLessThan(150);
    }

    @Test
    void doesNotLoadCjkFontsForLatinOnlyStories() throws IOException {
        File pdf = generate("Title", "<p>Plain Latin text.</p>");

        assertThat(Files.size(pdf.toPath())).isLessThan(200_000);
    }

    @Test
    void returnsAnAbsolutePathNamedAfterTitleAndId() {
        Fanfic fanfic = fanfic("For Her");

        File pdf = new File(service.generate(fanfic, List.of()));

        assertThat(pdf).isAbsolute().exists();
        assertThat(pdf.getName()).isEqualTo("For Her " + fanfic.getId() + ".pdf");
    }

    @Test
    void writesTitleDescriptionAndMetadataOnTheFirstPage() throws IOException {
        Fanfic fanfic = fanfic("For Her");
        fanfic.setRating("T");
        fanfic.setLanguage("English");
        fanfic.setGenre("Romance");
        fanfic.setCharacters("Harry P.");
        fanfic.setChaptersCount(2);
        fanfic.setWords(1000);

        try (PDDocument document = Loader.loadPDF(new File(service.generate(fanfic, List.of())))) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(document)).contains(
                    "For Her", "Description",
                    "Rated: T | English | Romance | Harry P. | Chapters: 2 | Words: 1000");
        }
    }

    @Test
    void startsEachChapterOnItsOwnPageInTheGivenOrder() throws IOException {
        Chapter titled = chapter(1, "<p>first body</p>");
        titled.setTitle("The Beginning");
        Chapter untitled = chapter(2, "<p>second body</p>");
        Chapter blankTitle = chapter(3, "<p>third body</p>");
        blankTitle.setTitle("   ");

        File pdf = new File(service.generate(fanfic("Title"), List.of(titled, untitled, blankTitle)));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(4);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).containsSubsequence(
                    "Chapter 1: The Beginning", "first body",
                    "Chapter 2", "second body",
                    "Chapter 3", "third body");
            assertThat(text).doesNotContain("Chapter 2:", "Chapter 3:");
        }
    }

    @Test
    void wrapsLongParagraphsOntoFurtherPages() throws IOException {
        File pdf = new File(service.generate(fanfic("Title"),
                List.of(chapter(1, "<p>" + "word ".repeat(5000) + "</p>"))));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(2);
        }
    }

    @Test
    void toleratesMissingTitleDescriptionAndChapterContent() throws IOException {
        Fanfic fanfic = fanfic(null);
        fanfic.setDescription(null);

        File pdf = new File(service.generate(fanfic, List.of(chapter(1, null))));

        assertThat(pdf.getName()).isEqualTo("story " + fanfic.getId() + ".pdf");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(document)).contains("Chapter 1");
        }
    }

    @Test
    void usesAPlaceholderFileNameForBlankTitles() {
        Fanfic fanfic = fanfic("   ");

        assertThat(new File(service.generate(fanfic, List.of())).getName())
                .isEqualTo("story " + fanfic.getId() + ".pdf");
    }

    @Test
    void failsClearlyWhenTheDirectoryCannotBeCreated() throws IOException {
        Path file = Files.createFile(pdfDir.resolve("occupied"));
        FfreaderProperties properties = new FfreaderProperties(
                null, null, new FfreaderProperties.Storage(null, file.resolve("sub").toString()));

        assertThatThrownBy(() -> new PdfGeneratorService(new HtmlToTextConverter(), properties))
                .isInstanceOf(FanficScrapeException.class)
                .hasMessageContaining("Could not create pdf directory");
    }

    @Test
    void failsClearlyWhenThePdfCannotBeSaved() throws IOException {
        Fanfic fanfic = fanfic("Title");
        // A directory squatting on the target path makes the save fail.
        Files.createDirectory(pdfDir.resolve("Title " + fanfic.getId() + ".pdf"));

        assertThatThrownBy(() -> service.generate(fanfic, List.of()))
                .isInstanceOf(FanficScrapeException.class)
                .hasMessageContaining(fanfic.getId().toString());
    }

    @Test
    void fileNameFitsLinuxLimitForLongNonLatinTitles() {
        File pdf = new File(service.generate(fanfic("你好".repeat(50)), List.of()));

        assertThat(pdf.getName().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(255);
    }

    @Test
    void truncatingTheFileNameDoesNotSplitAnEmoji() {
        Fanfic fanfic = fanfic("x".repeat(99) + "😀😀");

        File pdf = new File(service.generate(fanfic, List.of()));

        assertThat(pdf.getName()).isEqualTo("x".repeat(99) + "😀 " + fanfic.getId() + ".pdf");
    }

    @Test
    void replacesLoneSurrogatesInTheFileName() {
        Fanfic fanfic = fanfic("a\uD83Db");

        assertThat(new File(service.generate(fanfic, List.of())).getName())
                .isEqualTo("a_b " + fanfic.getId() + ".pdf");
    }

    @Test
    void keepsShortNonLatinTitlesWhole() {
        Fanfic fanfic = fanfic("你好".repeat(10));

        assertThat(new File(service.generate(fanfic, List.of())).getName())
                .isEqualTo("你好".repeat(10) + " " + fanfic.getId() + ".pdf");
    }

    private static Fanfic fanfic(String title) {
        Fanfic fanfic = new Fanfic();
        fanfic.setId(UUID.randomUUID());
        fanfic.setTitle(title);
        fanfic.setDescription("Description");
        return fanfic;
    }

    private static Chapter chapter(int number, String html) {
        Chapter chapter = new Chapter();
        chapter.setChapterNumber(number);
        chapter.setRawHtml(html);
        return chapter;
    }

    private String generateAndExtract(String title, String html) throws IOException {
        try (PDDocument document = Loader.loadPDF(generate(title, html))) {
            return new PDFTextStripper().getText(document);
        }
    }

    private File generate(String title, String html) {
        Fanfic fanfic = new Fanfic();
        fanfic.setId(UUID.randomUUID());
        fanfic.setTitle(title);
        fanfic.setDescription("Description");

        Chapter chapter = new Chapter();
        chapter.setChapterNumber(1);
        chapter.setRawHtml(html);

        return new File(service.generate(fanfic, List.of(chapter)));
    }
}
