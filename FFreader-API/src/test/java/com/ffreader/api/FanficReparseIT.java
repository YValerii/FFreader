package com.ffreader.api;

import com.ffreader.api.domain.Chapter;
import com.ffreader.api.domain.Fanfic;
import com.ffreader.api.domain.FanficStatus;
import com.ffreader.api.dto.CreateFanficRequest;
import com.ffreader.api.repository.ChapterRepository;
import com.ffreader.api.repository.FanficRepository;
import com.ffreader.api.service.FanficParsingService;
import com.ffreader.api.service.FanficService;
import com.ffreader.api.service.scraper.FanficScraperService;
import com.ffreader.api.service.scraper.ScrapedChapter;
import com.ffreader.api.service.scraper.ScrapedFanficInfo;
import com.ffreader.api.service.scraper.WebDriverFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs the parse pipeline against a real PostgreSQL with only the browser
 * replaced. Start the database first (docker compose up -d), then run:
 * mvnw test -Dtest=FanficReparseIT
 */
@SpringBootTest
class FanficReparseIT {

    @TempDir
    static Path storage;

    @DynamicPropertySource
    static void storageDirs(DynamicPropertyRegistry registry) {
        registry.add("ffreader.storage.extraction-dir", () -> storage.resolve("extractions").toString());
        registry.add("ffreader.storage.pdf-dir", () -> storage.resolve("pdfs").toString());
    }

    @MockitoBean WebDriverFactory webDriverFactory;
    @MockitoBean FanficScraperService scraperService;

    @Autowired FanficService fanficService;
    @Autowired FanficParsingService parsingService;
    @Autowired FanficRepository fanficRepository;
    @Autowired ChapterRepository chapterRepository;

    private String url;
    private UUID createdId;

    @BeforeEach
    void setUp() {
        String storyId = String.valueOf(ThreadLocalRandom.current().nextInt(100_000_000, 999_999_999));
        url = "https://www.fanfiction.net/s/" + storyId + "/1/Story";

        when(webDriverFactory.newDriver()).thenAnswer(call -> mock(WebDriver.class));
        when(scraperService.fetchInfo(any(), any())).thenReturn(new ScrapedFanficInfo(
                storyId, "Part 1: Заголовок", "Description", "T", "English", "Romance", "A., B.",
                2, 1000, 1, 2, 3, null));
        chapterText("first");
    }

    @AfterEach
    void cleanUp() {
        if (createdId != null) {
            awaitJobFinished(createdId);
            fanficService.delete(createdId);
        }
    }

    @Test
    void resubmittingAStoryReplacesItsChaptersAndPdf() throws Exception {
        Fanfic fanfic = submitAndAwaitParsed();
        Path firstPdf = Path.of(fanfic.getPdfPath());
        List<Chapter> firstChapters = chapters();
        assertThat(firstChapters).hasSize(2);
        assertThat(firstChapters.getFirst().getRawHtml()).contains("first");
        assertThat(firstPdf).exists();
        assertThat(extractionFiles()).hasSize(2);

        chapterText("second");
        fanfic = submitAndAwaitParsed();

        List<Chapter> secondChapters = chapters();
        assertThat(secondChapters).hasSize(2);
        assertThat(secondChapters).allMatch(chapter -> chapter.getRawHtml().contains("second"));
        assertThat(fanfic.getLoadedChaptersCount()).isEqualTo(2);
        assertThat(fanfic.getErrorMessage()).isNull();
        assertThat(Path.of(fanfic.getPdfPath())).exists();
        assertThat(extractionFiles()).hasSize(2);
    }

    @Test
    void chaptersSurviveTheStatusUpdatesThatFollowThem() {
        submitAndAwaitParsed();

        assertThat(chapters()).extracting(Chapter::getChapterNumber).containsExactly(1, 2);
    }

    @Test
    void doesNotStartASecondJobWhileOneIsRunning() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(scraperService.fetchChapterContent(any(), any(), anyInt())).thenAnswer(call -> {
            release.await(30, TimeUnit.SECONDS);
            return new ScrapedChapter(call.getArgument(2), "<p>text</p>");
        });

        createdId = fanficService.create(new CreateFanficRequest(url)).getId();
        fanficService.create(new CreateFanficRequest(url));
        release.countDown();

        awaitJobFinished(createdId);
        verify(scraperService, times(1)).fetchInfo(any(), any());
        assertThat(fanficService.get(createdId).getStatus()).isEqualTo(FanficStatus.PARSED);
    }

    private Fanfic submitAndAwaitParsed() {
        createdId = fanficService.create(new CreateFanficRequest(url)).getId();
        awaitJobFinished(createdId);
        Fanfic fanfic = fanficService.get(createdId);
        assertThat(fanfic.getStatus()).as(fanfic.getErrorMessage()).isEqualTo(FanficStatus.PARSED);
        return fanfic;
    }

    private void awaitJobFinished(UUID id) {
        await().atMost(Duration.ofSeconds(60)).until(() -> {
            if (parsingService.tryReserve(id)) {
                parsingService.release(id);
                return true;
            }
            return false;
        });
    }

    private void chapterText(String text) {
        when(scraperService.fetchChapterContent(any(), any(), anyInt())).thenAnswer(call ->
                new ScrapedChapter(call.getArgument(2), "<p>" + text + "</p>"));
    }

    private List<Chapter> chapters() {
        return chapterRepository.findByFanficIdOrderByChapterNumberAsc(createdId);
    }

    private List<Path> extractionFiles() throws Exception {
        List<String> names = chapters().stream().map(Chapter::getExtractionFileName).toList();
        try (var files = Files.list(storage.resolve("extractions"))) {
            return files.filter(file -> names.contains(file.getFileName().toString())).toList();
        }
    }
}
