package com.ffreader.api.service;

import com.ffreader.api.config.FfreaderProperties;
import com.ffreader.api.exception.FanficScrapeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractionFileServiceTest {

    private static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir
    Path dir;

    private ExtractionFileService service;

    @BeforeEach
    void setUp() {
        service = serviceFor(dir);
    }

    @Test
    void createsTheDirectoryIfMissing() {
        Path nested = dir.resolve("a").resolve("b");

        serviceFor(nested);

        assertThat(nested).isDirectory();
    }

    @Test
    void failsClearlyWhenTheDirectoryCannotBeCreated() throws IOException {
        Path file = Files.createFile(dir.resolve("occupied"));

        assertThatThrownBy(() -> serviceFor(file.resolve("sub")))
                .isInstanceOf(FanficScrapeException.class)
                .hasMessageContaining("Could not create extraction directory");
    }

    @Test
    void namesFilesByRunStoryAndChapter() {
        assertThat(service.writeChapter(RUN, "For Her", 3, "x")).isEqualTo(RUN + "_For_Her_3.txt");
    }

    @Test
    void replacesUnsafeCharactersInTheStoryName() {
        assertThat(service.writeChapter(RUN, "  ../What: Is/This?  ", 1, "x"))
                .isEqualTo(RUN + "__What_Is_This__1.txt");
    }

    @Test
    void usesAPlaceholderForMissingStoryNames() {
        assertThat(service.writeChapter(RUN, null, 1, "x")).isEqualTo(RUN + "_story_1.txt");
        assertThat(service.writeChapter(RUN, "   ", 2, "x")).isEqualTo(RUN + "_story_2.txt");
    }

    @Test
    void readsBackWhatItWroteInUtf8() {
        String html = "<p>Привет 你好 안녕</p>";

        String fileName = service.writeChapter(RUN, "Story", 1, html);

        assertThat(service.readChapter(fileName)).isEqualTo(html);
    }

    @Test
    void failsClearlyWhenReadingAMissingFile() {
        assertThatThrownBy(() -> service.readChapter("missing.txt"))
                .isInstanceOf(FanficScrapeException.class)
                .hasMessageContaining("Could not read extraction file");
    }

    @Test
    void failsClearlyWhenWritingIsImpossible() throws IOException {
        Files.createDirectory(dir.resolve(RUN + "_Story_1.txt"));

        assertThatThrownBy(() -> service.writeChapter(RUN, "Story", 1, "x"))
                .isInstanceOf(FanficScrapeException.class)
                .hasMessageContaining("Could not write extraction file");
    }

    @Test
    void deletesAFile() {
        String fileName = service.writeChapter(RUN, "Story", 1, "x");

        service.deleteChapter(fileName);

        assertThat(dir.resolve(fileName)).doesNotExist();
    }

    @Test
    void deleteIgnoresNullAndMissingFiles() {
        assertThatCode(() -> {
            service.deleteChapter(null);
            service.deleteChapter("missing.txt");
        }).doesNotThrowAnyException();
    }

    @Test
    void deleteOnlyWarnsWhenTheFileCannotBeRemoved() throws IOException {
        Path busy = Files.createDirectory(dir.resolve("busy"));
        Files.createFile(busy.resolve("child"));

        assertThatCode(() -> service.deleteChapter("busy")).doesNotThrowAnyException();
        assertThat(busy).exists();
    }

    @Test
    void acceptsVeryLongStoryNames() {
        String fileName = service.writeChapter(RUN, "x".repeat(1000), 1, "content");

        assertThat(service.readChapter(fileName)).isEqualTo("content");
        assertThat(fileName).hasSizeLessThan(255);
    }

    private static ExtractionFileService serviceFor(Path path) {
        return new ExtractionFileService(new FfreaderProperties(
                null, null, new FfreaderProperties.Storage(path.toString(), path.toString())));
    }
}
