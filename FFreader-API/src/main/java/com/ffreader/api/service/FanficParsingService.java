package com.ffreader.api.service;

import com.ffreader.api.domain.Chapter;
import com.ffreader.api.domain.Fanfic;
import com.ffreader.api.domain.FanficStatus;
import com.ffreader.api.exception.FanficScrapeException;
import com.ffreader.api.repository.ChapterRepository;
import com.ffreader.api.repository.FanficRepository;
import com.ffreader.api.service.pdf.PdfGeneratorService;
import com.ffreader.api.service.scraper.FanficScraperService;
import com.ffreader.api.service.scraper.FanficUrl;
import com.ffreader.api.service.scraper.ScrapedChapter;
import com.ffreader.api.service.scraper.ScrapedFanficInfo;
import com.ffreader.api.service.scraper.WebDriverFactory;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Runs the whole "scrape -> persist chapters -> render PDF" pipeline for one
 * fanfic on a background thread (see AsyncConfig#fanficTaskExecutor), so
 * several stories can be parsed in parallel while the REST API stays responsive.
 */
@Slf4j
@Service
public class FanficParsingService {

    private static final int MAX_PARSE_RETRIES = 10;

    private final FanficRepository fanficRepository;
    private final ChapterRepository chapterRepository;
    private final FanficScraperService scraperService;
    private final WebDriverFactory webDriverFactory;
    private final ExtractionFileService extractionFileService;
    private final PdfGeneratorService pdfGeneratorService;
    private final Set<UUID> activeJobs = ConcurrentHashMap.newKeySet();

    public FanficParsingService(FanficRepository fanficRepository,
                                ChapterRepository chapterRepository,
                                FanficScraperService scraperService,
                                WebDriverFactory webDriverFactory,
                                ExtractionFileService extractionFileService,
                                PdfGeneratorService pdfGeneratorService) {
        this.fanficRepository = fanficRepository;
        this.chapterRepository = chapterRepository;
        this.scraperService = scraperService;
        this.webDriverFactory = webDriverFactory;
        this.extractionFileService = extractionFileService;
        this.pdfGeneratorService = pdfGeneratorService;
    }

    /**
     * Reserves the fanfic for a new parse job.
     *
     * @return false if a job for this fanfic is already queued or running
     */
    public boolean tryReserve(UUID fanficId) {
        return activeJobs.add(fanficId);
    }

    /** Gives up a reservation whose job could not be submitted. */
    public void release(UUID fanficId) {
        activeJobs.remove(fanficId);
    }

    @Async("fanficTaskExecutor")
    public void parseAsync(UUID fanficId) {
        try {
            parse(fanficId);
        } finally {
            release(fanficId);
        }
    }

    private void parse(UUID fanficId) {
        Fanfic fanfic = fanficRepository.findById(fanficId).orElse(null);
        if (fanfic == null) {
            log.warn("Fanfic {} disappeared before parsing could start", fanficId);
            return;
        }

        markStatus(fanfic, FanficStatus.PARSING, null);
        List<Chapter> chapters = new ArrayList<>();
        int chapterCount = 0;

        boolean parsingFailed = false;
        String errorMessage = null;

        try {
            clearPreviousResults(fanfic);

            FanficUrl url = FanficUrl.parse(fanfic.getSourceUrl());

            // Step 1: story metadata (rating, description, chapter count, etc.)
            ScrapedFanficInfo info = withDriver(driver -> scraperService.fetchInfo(driver, url));
            applyInfo(fanfic, info);
            fanfic = fanficRepository.save(fanfic);

            // Step 2: walk every chapter, saving each to a temp extraction file + DB row
            UUID extractionRunId = UUID.randomUUID();
            chapterCount = info.chaptersCount() != null ? info.chaptersCount() : 1;
            if (chapterCount < 1) {
                throw new FanficScrapeException("The story page reported no chapters");
            }

            int chapterNumber = 1;
            int consecutiveErrors = 0;

            // Using a while loop to control exactly when we advance to the next chapter
            while (chapterNumber <= chapterCount) {
                try {
                    int currentChapter = chapterNumber;
                    ScrapedChapter scraped = withDriver(driver ->
                            scraperService.fetchChapterContent(driver, url, currentChapter));

                    String fileName = extractionFileService.writeChapter(
                            extractionRunId, info.title(), chapterNumber, scraped.rawHtml());

                    Chapter chapter = new Chapter();
                    chapter.setFanfic(fanfic);
                    chapter.setChapterNumber(chapterNumber);
                    chapter.setRawHtml(scraped.rawHtml());
                    chapter.setExtractionFileName(fileName);
                    chapters.add(chapterRepository.save(chapter));

                    // Success: move to the next chapter and reset the error counter
                    chapterNumber++;
                    consecutiveErrors = 0;

                } catch (Exception e) {
                    consecutiveErrors++;
                    log.warn("Failed to parse chapter {} for fanfic {} (Attempt {}/{}). Error: {}",
                            chapterNumber, fanficId, consecutiveErrors, MAX_PARSE_RETRIES, e.getMessage());

                    if (consecutiveErrors >= MAX_PARSE_RETRIES) {
                        throw new RuntimeException(String.format("Aborting: failed to parse chapter %d after %d attempts",
                                chapterNumber, MAX_PARSE_RETRIES), e);
                    }

                    // Optional: You could add a Thread.sleep(1000) here to add a brief backoff delay before hitting the site again
                }
            }

            log.info("Successfully parsed fanfic {} ({} chapters)", fanfic.getExternalId(), chapterCount);

        } catch (Exception e) {
            log.error("Failed to parse fanfic {}", fanficId, e);
            parsingFailed = true;
            errorMessage = e.getMessage();
        } finally {
            // Step 3: turn the extracted chapters into one clean, readable PDF
            // Fix: Only generate PDF and mark as PARSED/PARSED_PARTIALLY if we actually saved chapters
            if (!chapters.isEmpty()) {
                try {
                    String pdfPath = pdfGeneratorService.generate(fanfic, chapters);
                    fanfic.setPdfPath(pdfPath);
                } catch (Exception pdfEx) {
                    log.error("Failed to generate PDF for fanfic {}", fanficId, pdfEx);
                    parsingFailed = true;
                    errorMessage = errorMessage == null ? pdfEx.getMessage() : errorMessage + "; PDF Error: " + pdfEx.getMessage();
                }

                fanfic.setLoadedChaptersCount(chapters.size());

                FanficStatus finalStatus = (!parsingFailed && chapters.size() == chapterCount)
                        ? FanficStatus.PARSED
                        : FanficStatus.PARSED_PARTIALLY;

                markStatus(fanfic, finalStatus, errorMessage);
            } else {
                // Total failure (no chapters loaded at all)
                markStatus(fanfic, FanficStatus.FAILED, errorMessage);
            }
        }
    }

    /** Runs one page load in its own browser, which is always closed afterwards. */
    private <T> T withDriver(Function<WebDriver, T> work) {
        WebDriver driver = webDriverFactory.newDriver();
        try {
            return work.apply(driver);
        } finally {
            try {
                driver.quit();
            } catch (Exception e) {
                log.warn("Could not close browser: {}", e.getMessage());
            }
        }
    }

    /** A re-submitted story starts again from nothing, so its new chapters don't clash with the old ones. */
    private void clearPreviousResults(Fanfic fanfic) throws IOException {
        List<Chapter> previous = chapterRepository.findByFanficIdOrderByChapterNumberAsc(fanfic.getId());
        for (Chapter chapter : previous) {
            extractionFileService.deleteChapter(chapter.getExtractionFileName());
        }
        chapterRepository.deleteAllInBatch(previous);

        if (fanfic.getPdfPath() != null) {
            Files.deleteIfExists(Path.of(fanfic.getPdfPath()));
            fanfic.setPdfPath(null);
        }
        fanfic.setLoadedChaptersCount(null);
    }

    private void applyInfo(Fanfic fanfic, ScrapedFanficInfo info) {
        if (info.externalId() != null) fanfic.setExternalId(info.externalId());
        fanfic.setTitle(info.title());
        fanfic.setDescription(info.description());
        fanfic.setRating(info.rating());
        fanfic.setLanguage(info.language());
        fanfic.setGenre(info.genre());
        fanfic.setCharacters(info.characters());
        fanfic.setChaptersCount(info.chaptersCount());
        fanfic.setWords(info.words());
        fanfic.setReviews(info.reviews());
        fanfic.setFavs(info.favs());
        fanfic.setFollows(info.follows());
        fanfic.setPublishedAt(info.publishedAt());
    }

    private void markStatus(Fanfic fanfic, FanficStatus status, String errorMessage) {
        fanfic.setStatus(status);
        fanfic.setErrorMessage(errorMessage);
        fanfic.setUpdatedAt(Instant.now());
        fanficRepository.save(fanfic);
    }
}