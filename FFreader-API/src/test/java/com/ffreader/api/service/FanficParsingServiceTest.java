package com.ffreader.api.service;

import com.ffreader.api.domain.Chapter;
import com.ffreader.api.domain.Fanfic;
import com.ffreader.api.domain.FanficStatus;
import com.ffreader.api.exception.FanficScrapeException;
import com.ffreader.api.repository.ChapterRepository;
import com.ffreader.api.repository.FanficRepository;
import com.ffreader.api.service.pdf.PdfGeneratorService;
import com.ffreader.api.service.scraper.FanficScraperService;
import com.ffreader.api.service.scraper.ScrapedChapter;
import com.ffreader.api.service.scraper.ScrapedFanficInfo;
import com.ffreader.api.service.scraper.WebDriverFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FanficParsingServiceTest {

    private static final UUID ID = UUID.randomUUID();

    @Mock FanficRepository fanficRepository;
    @Mock ChapterRepository chapterRepository;
    @Mock FanficScraperService scraperService;
    @Mock WebDriverFactory webDriverFactory;
    @Mock ExtractionFileService extractionFileService;
    @Mock PdfGeneratorService pdfGeneratorService;

    private final List<WebDriver> drivers = new ArrayList<>();
    private final Fanfic fanfic = new Fanfic();
    private FanficParsingService service;

    @BeforeEach
    void setUp() {
        fanfic.setId(ID);
        fanfic.setSourceUrl("https://www.fanfiction.net/s/123/1/Story");

        when(fanficRepository.findById(ID)).thenReturn(Optional.of(fanfic));
        when(fanficRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(chapterRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(chapterRepository.findByFanficIdOrderByChapterNumberAsc(ID)).thenReturn(List.of());
        when(pdfGeneratorService.generate(any(), any())).thenReturn("story.pdf");
        when(webDriverFactory.newDriver()).thenAnswer(call -> {
            WebDriver driver = mock(WebDriver.class);
            drivers.add(driver);
            return driver;
        });

        service = new FanficParsingService(fanficRepository, chapterRepository, scraperService,
                webDriverFactory, extractionFileService, pdfGeneratorService);
    }

    @Test
    void closesTheBrowserAfterEveryPageLoad() {
        when(scraperService.fetchInfo(any(), any())).thenReturn(info(2));
        when(scraperService.fetchChapterContent(any(), any(), anyInt()))
                .thenAnswer(call -> new ScrapedChapter(call.getArgument(2), "<p>text</p>"));

        service.parseAsync(ID);

        assertThat(drivers).hasSize(3);
        drivers.forEach(driver -> verify(driver).quit());
        assertThat(fanfic.getStatus()).isEqualTo(FanficStatus.PARSED);
    }

    @Test
    void closesTheBrowserWhenAChapterAttemptFails() {
        when(scraperService.fetchInfo(any(), any())).thenReturn(info(1));
        when(scraperService.fetchChapterContent(any(), any(), eq(1)))
                .thenThrow(new FanficScrapeException("blocked"))
                .thenReturn(new ScrapedChapter(1, "<p>text</p>"));

        service.parseAsync(ID);

        assertThat(drivers).hasSize(3);
        drivers.forEach(driver -> verify(driver).quit());
        assertThat(fanfic.getStatus()).isEqualTo(FanficStatus.PARSED);
    }

    @Test
    void closesTheBrowserWhenMetadataCannotBeRead() {
        when(scraperService.fetchInfo(any(), any())).thenThrow(new FanficScrapeException("blocked"));

        service.parseAsync(ID);

        assertThat(drivers).hasSize(1);
        verify(drivers.getFirst()).quit();
        assertThat(fanfic.getStatus()).isEqualTo(FanficStatus.FAILED);
    }

    @Test
    void keepsChaptersThatLoadedWhenALaterOneKeepsFailing() {
        when(scraperService.fetchInfo(any(), any())).thenReturn(info(2));
        when(scraperService.fetchChapterContent(any(), any(), eq(1)))
                .thenReturn(new ScrapedChapter(1, "<p>text</p>"));
        when(scraperService.fetchChapterContent(any(), any(), eq(2)))
                .thenThrow(new FanficScrapeException("blocked"));

        service.parseAsync(ID);

        assertThat(fanfic.getStatus()).isEqualTo(FanficStatus.PARSED_PARTIALLY);
        assertThat(fanfic.getLoadedChaptersCount()).isEqualTo(1);
        drivers.forEach(driver -> verify(driver).quit());
    }

    @Test
    void releasesTheReservationWhenTheJobFinishes() {
        when(scraperService.fetchInfo(any(), any())).thenThrow(new FanficScrapeException("blocked"));
        assertThat(service.tryReserve(ID)).isTrue();
        assertThat(service.tryReserve(ID)).isFalse();

        service.parseAsync(ID);

        assertThat(service.tryReserve(ID)).isTrue();
    }

    @Test
    void releasesTheReservationEvenWhenTheJobThrows() {
        when(fanficRepository.findById(ID)).thenThrow(new IllegalStateException("db down"));
        service.tryReserve(ID);

        assertThatThrownBy(() -> service.parseAsync(ID)).isInstanceOf(IllegalStateException.class);

        assertThat(service.tryReserve(ID)).isTrue();
    }

    @Test
    void releaseFreesAReservation() {
        service.tryReserve(ID);

        service.release(ID);

        assertThat(service.tryReserve(ID)).isTrue();
    }

    @Test
    void doesNothingWhenTheFanficWasDeletedBeforeTheJobStarted() {
        when(fanficRepository.findById(ID)).thenReturn(Optional.empty());

        service.parseAsync(ID);

        verify(webDriverFactory, never()).newDriver();
        verify(fanficRepository, never()).save(any());
    }

    @Test
    void marksTheStoryAsParsingBeforeScraping() {
        List<FanficStatus> statuses = new ArrayList<>();
        when(fanficRepository.save(any())).thenAnswer(call -> {
            statuses.add(call.<Fanfic>getArgument(0).getStatus());
            return call.getArgument(0);
        });
        when(scraperService.fetchInfo(any(), any())).thenReturn(info(1));
        when(scraperService.fetchChapterContent(any(), any(), anyInt()))
                .thenReturn(new ScrapedChapter(1, "<p>text</p>"));

        service.parseAsync(ID);

        assertThat(statuses.getFirst()).isEqualTo(FanficStatus.PARSING);
        assertThat(statuses.getLast()).isEqualTo(FanficStatus.PARSED);
    }

    @Test
    void copiesScrapedMetadataAndThePdfPathOntoTheStory() {
        Instant published = Instant.ofEpochSecond(1_000);
        when(scraperService.fetchInfo(any(), any())).thenReturn(new ScrapedFanficInfo(
                "999", "Title", "Desc", "M", "French", "Drama", "C., D.", 1, 10, 20, 30, 40, published));
        when(scraperService.fetchChapterContent(any(), any(), anyInt()))
                .thenReturn(new ScrapedChapter(1, "<p>text</p>"));

        service.parseAsync(ID);

        assertThat(fanfic.getExternalId()).isEqualTo("999");
        assertThat(fanfic.getTitle()).isEqualTo("Title");
        assertThat(fanfic.getDescription()).isEqualTo("Desc");
        assertThat(fanfic.getRating()).isEqualTo("M");
        assertThat(fanfic.getLanguage()).isEqualTo("French");
        assertThat(fanfic.getGenre()).isEqualTo("Drama");
        assertThat(fanfic.getCharacters()).isEqualTo("C., D.");
        assertThat(fanfic.getChaptersCount()).isEqualTo(1);
        assertThat(fanfic.getWords()).isEqualTo(10);
        assertThat(fanfic.getReviews()).isEqualTo(20);
        assertThat(fanfic.getFavs()).isEqualTo(30);
        assertThat(fanfic.getFollows()).isEqualTo(40);
        assertThat(fanfic.getPublishedAt()).isEqualTo(published);
        assertThat(fanfic.getPdfPath()).isEqualTo("story.pdf");
        assertThat(fanfic.getLoadedChaptersCount()).isEqualTo(1);
        assertThat(fanfic.getErrorMessage()).isNull();
    }

    @Test
    void keepsTheExistingExternalIdWhenThePageHasNone() {
        fanfic.setExternalId("123");
        when(scraperService.fetchInfo(any(), any())).thenReturn(new ScrapedFanficInfo(
                null, "Title", "", null, null, null, null, 1, null, null, null, null, null));
        when(scraperService.fetchChapterContent(any(), any(), anyInt()))
                .thenReturn(new ScrapedChapter(1, "<p>text</p>"));

        service.parseAsync(ID);

        assertThat(fanfic.getExternalId()).isEqualTo("123");
    }

    @Test
    void treatsAnUnknownChapterCountAsOneChapter() {
        when(scraperService.fetchInfo(any(), any())).thenReturn(new ScrapedFanficInfo(
                "123", "Title", "", null, null, null, null, null, null, null, null, null, null));
        when(scraperService.fetchChapterContent(any(), any(), anyInt()))
                .thenReturn(new ScrapedChapter(1, "<p>text</p>"));

        service.parseAsync(ID);

        verify(scraperService).fetchChapterContent(any(), any(), eq(1));
        assertThat(fanfic.getStatus()).isEqualTo(FanficStatus.PARSED);
    }

    @Test
    void savesEveryChapterInOrderUnderOneExtractionRun() {
        when(scraperService.fetchInfo(any(), any())).thenReturn(info(2));
        when(scraperService.fetchChapterContent(any(), any(), anyInt()))
                .thenAnswer(call -> new ScrapedChapter(call.getArgument(2), "<p>ch" + call.getArgument(2) + "</p>"));
        when(extractionFileService.writeChapter(any(), any(), anyInt(), any()))
                .thenAnswer(call -> "file" + call.getArgument(2) + ".txt");

        service.parseAsync(ID);

        ArgumentCaptor<UUID> runIds = ArgumentCaptor.forClass(UUID.class);
        verify(extractionFileService, times(2)).writeChapter(runIds.capture(), eq("Story"), anyInt(), any());
        assertThat(runIds.getAllValues().getFirst()).isEqualTo(runIds.getAllValues().getLast());

        ArgumentCaptor<Chapter> saved = ArgumentCaptor.forClass(Chapter.class);
        verify(chapterRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Chapter::getChapterNumber).containsExactly(1, 2);
        assertThat(saved.getAllValues()).extracting(Chapter::getRawHtml)
                .containsExactly("<p>ch1</p>", "<p>ch2</p>");
        assertThat(saved.getAllValues()).extracting(Chapter::getExtractionFileName)
                .containsExactly("file1.txt", "file2.txt");
        assertThat(saved.getAllValues()).allSatisfy(chapter -> assertThat(chapter.getFanfic()).isSameAs(fanfic));

        verify(pdfGeneratorService).generate(fanfic, saved.getAllValues());
    }

    @Test
    void givesUpOnAChapterAfterTenAttemptsAndReportsWhy() {
        when(scraperService.fetchInfo(any(), any())).thenReturn(info(3));
        when(scraperService.fetchChapterContent(any(), any(), eq(1)))
                .thenReturn(new ScrapedChapter(1, "<p>text</p>"));
        when(scraperService.fetchChapterContent(any(), any(), eq(2)))
                .thenThrow(new FanficScrapeException("blocked"));

        service.parseAsync(ID);

        verify(scraperService, times(10)).fetchChapterContent(any(), any(), eq(2));
        verify(scraperService, never()).fetchChapterContent(any(), any(), eq(3));
        assertThat(fanfic.getErrorMessage()).isEqualTo("Aborting: failed to parse chapter 2 after 10 attempts");
    }

    @Test
    void theRetryLimitCountsConsecutiveFailuresPerChapter() {
        when(scraperService.fetchInfo(any(), any())).thenReturn(info(2));
        Map<Integer, Integer> attempts = new HashMap<>();
        when(scraperService.fetchChapterContent(any(), any(), anyInt())).thenAnswer(call -> {
            int chapter = call.getArgument(2);
            if (attempts.merge(chapter, 1, Integer::sum) < 10) {
                throw new FanficScrapeException("flaky");
            }
            return new ScrapedChapter(chapter, "<p>text</p>");
        });

        service.parseAsync(ID);

        assertThat(fanfic.getStatus()).isEqualTo(FanficStatus.PARSED);
        assertThat(fanfic.getLoadedChaptersCount()).isEqualTo(2);
    }

    @Test
    void retriesAChapterWhenWritingItsFileFails() {
        when(scraperService.fetchInfo(any(), any())).thenReturn(info(1));
        when(scraperService.fetchChapterContent(any(), any(), anyInt()))
                .thenReturn(new ScrapedChapter(1, "<p>text</p>"));
        when(extractionFileService.writeChapter(any(), any(), anyInt(), any()))
                .thenThrow(new FanficScrapeException("disk full"))
                .thenReturn("file.txt");

        service.parseAsync(ID);

        verify(chapterRepository, times(1)).save(any());
        assertThat(fanfic.getStatus()).isEqualTo(FanficStatus.PARSED);
    }

    @Test
    void failsWithTheScraperMessageWhenNothingLoaded() {
        when(scraperService.fetchInfo(any(), any())).thenThrow(new FanficScrapeException("blocked"));

        service.parseAsync(ID);

        assertThat(fanfic.getStatus()).isEqualTo(FanficStatus.FAILED);
        assertThat(fanfic.getErrorMessage()).isEqualTo("blocked");
        verify(pdfGeneratorService, never()).generate(any(), any());
    }

    @Test
    void failsWhenTheBrowserCannotStart() {
        when(webDriverFactory.newDriver()).thenThrow(new IllegalStateException("no chrome"));

        service.parseAsync(ID);

        assertThat(fanfic.getStatus()).isEqualTo(FanficStatus.FAILED);
        assertThat(fanfic.getErrorMessage()).isEqualTo("no chrome");
    }

    @Test
    void failsOnAnUnparseableStoredUrl() {
        fanfic.setSourceUrl("https://example.com/nope");

        service.parseAsync(ID);

        assertThat(fanfic.getStatus()).isEqualTo(FanficStatus.FAILED);
        verify(webDriverFactory, never()).newDriver();
    }

    @Test
    void aBrowserThatWontCloseDoesNotFailTheJob() {
        when(webDriverFactory.newDriver()).thenAnswer(call -> {
            WebDriver driver = mock(WebDriver.class);
            doThrow(new IllegalStateException("already gone")).when(driver).quit();
            return driver;
        });
        when(scraperService.fetchInfo(any(), any())).thenReturn(info(1));
        when(scraperService.fetchChapterContent(any(), any(), anyInt()))
                .thenReturn(new ScrapedChapter(1, "<p>text</p>"));

        service.parseAsync(ID);

        assertThat(fanfic.getStatus()).isEqualTo(FanficStatus.PARSED);
    }

    @Test
    void reparsingClearsThePreviousChaptersFilesAndPdf(@TempDir Path dir) throws IOException {
        Path oldPdf = Files.createFile(dir.resolve("old.pdf"));
        fanfic.setPdfPath(oldPdf.toString());
        fanfic.setLoadedChaptersCount(5);
        Chapter previous = new Chapter();
        previous.setExtractionFileName("old.txt");
        when(chapterRepository.findByFanficIdOrderByChapterNumberAsc(ID)).thenReturn(List.of(previous));
        when(scraperService.fetchInfo(any(), any())).thenThrow(new FanficScrapeException("blocked"));

        service.parseAsync(ID);

        verify(extractionFileService).deleteChapter("old.txt");
        verify(chapterRepository).deleteAllInBatch(List.of(previous));
        assertThat(oldPdf).doesNotExist();
        assertThat(fanfic.getPdfPath()).isNull();
        assertThat(fanfic.getLoadedChaptersCount()).isNull();
    }

    @Test
    void failsWhenThePreviousPdfCannotBeRemoved(@TempDir Path dir) throws IOException {
        Path undeletable = Files.createDirectory(dir.resolve("old.pdf"));
        Files.createFile(undeletable.resolve("child"));
        fanfic.setPdfPath(undeletable.toString());

        service.parseAsync(ID);

        assertThat(fanfic.getStatus()).isEqualTo(FanficStatus.FAILED);
        verify(webDriverFactory, never()).newDriver();
    }

    @Test
    void combinesScrapeAndPdfErrors() {
        when(scraperService.fetchInfo(any(), any())).thenReturn(info(2));
        when(scraperService.fetchChapterContent(any(), any(), eq(1)))
                .thenReturn(new ScrapedChapter(1, "<p>text</p>"));
        when(scraperService.fetchChapterContent(any(), any(), eq(2)))
                .thenThrow(new FanficScrapeException("blocked"));
        when(pdfGeneratorService.generate(any(), any())).thenThrow(new FanficScrapeException("no disk"));

        service.parseAsync(ID);

        assertThat(fanfic.getStatus()).isEqualTo(FanficStatus.PARSED_PARTIALLY);
        assertThat(fanfic.getErrorMessage())
                .isEqualTo("Aborting: failed to parse chapter 2 after 10 attempts; PDF Error: no disk");
    }

    @Test
    void reportsAPdfFailureOnAFullyScrapedStory() {
        when(scraperService.fetchInfo(any(), any())).thenReturn(info(1));
        when(scraperService.fetchChapterContent(any(), any(), anyInt()))
                .thenReturn(new ScrapedChapter(1, "<p>text</p>"));
        when(pdfGeneratorService.generate(any(), any())).thenThrow(new FanficScrapeException("no disk"));

        service.parseAsync(ID);

        assertThat(fanfic.getStatus()).isEqualTo(FanficStatus.PARSED_PARTIALLY);
        assertThat(fanfic.getErrorMessage()).isEqualTo("no disk");
        assertThat(fanfic.getPdfPath()).isNull();
        assertThat(fanfic.getLoadedChaptersCount()).isEqualTo(1);
    }

    @Test
    void explainsTheFailureWhenThePageReportsNoChapters() {
        when(scraperService.fetchInfo(any(), any())).thenReturn(info(0));

        service.parseAsync(ID);

        assertThat(fanfic.getStatus()).isEqualTo(FanficStatus.FAILED);
        assertThat(fanfic.getErrorMessage()).isEqualTo("The story page reported no chapters");
    }

    private ScrapedFanficInfo info(int chapters) {
        return new ScrapedFanficInfo("123", "Story", "Description", "T", "English", "Romance",
                "A., B.", chapters, 1000, 1, 2, 3, null);
    }
}
