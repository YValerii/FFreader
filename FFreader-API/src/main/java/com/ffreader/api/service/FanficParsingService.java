package com.ffreader.api.service;

import com.ffreader.api.domain.Chapter;
import com.ffreader.api.domain.Fanfic;
import com.ffreader.api.domain.FanficStatus;
import com.ffreader.api.repository.ChapterRepository;
import com.ffreader.api.repository.FanficRepository;
import com.ffreader.api.service.pdf.PdfGeneratorService;
import com.ffreader.api.service.scraper.FanficScraperService;
import com.ffreader.api.service.scraper.FanficUrl;
import com.ffreader.api.service.scraper.ScrapedChapter;
import com.ffreader.api.service.scraper.ScrapedFanficInfo;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import com.ffreader.api.service.scraper.WebDriverFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runs the whole "scrape -> persist chapters -> render PDF" pipeline for one
 * fanfic on a background thread (see AsyncConfig#fanficTaskExecutor), so
 * several stories can be parsed in parallel while the REST API stays responsive.
 */
@Slf4j
@Service
public class FanficParsingService {

    private final FanficRepository fanficRepository;
    private final ChapterRepository chapterRepository;
    private final FanficScraperService scraperService;
    private final WebDriverFactory webDriverFactory;
    private final ExtractionFileService extractionFileService;
    private final PdfGeneratorService pdfGeneratorService;

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

    @Async("fanficTaskExecutor")
    public void parseAsync(UUID fanficId) {
        Fanfic fanfic = fanficRepository.findById(fanficId).orElse(null);
        if (fanfic == null) {
            log.warn("Fanfic {} disappeared before parsing could start", fanficId);
            return;
        }

        markStatus(fanfic, FanficStatus.PARSING, null);
        WebDriver driver = null;
        try {
            driver = webDriverFactory.newDriver();
            FanficUrl url = FanficUrl.parse(fanfic.getSourceUrl());

            // Step 1: story metadata (rating, description, chapter count, etc.)
            ScrapedFanficInfo info = scraperService.fetchInfo(driver, url);
            applyInfo(fanfic, info);
            fanfic = fanficRepository.save(fanfic);

            // Step 2: walk every chapter, saving each to a temp extraction file + DB row
            UUID extractionRunId = UUID.randomUUID();
            int chapterCount = info.chaptersCount() != null ? info.chaptersCount() : 1;
            List<Chapter> chapters = new ArrayList<>();

            for (int chapterNumber = 1; chapterNumber <= chapterCount; chapterNumber++) {
                ScrapedChapter scraped = scraperService.fetchChapterContent(driver, url, chapterNumber);

                String fileName = extractionFileService.writeChapter(
                        extractionRunId, info.title(), chapterNumber, scraped.rawHtml());

                Chapter chapter = new Chapter();
                chapter.setFanfic(fanfic);
                chapter.setChapterNumber(chapterNumber);
                chapter.setRawHtml(scraped.rawHtml());
                chapter.setExtractionFileName(fileName);
                chapters.add(chapterRepository.save(chapter));
            }

            // Step 3: turn the extracted chapters into one clean, readable PDF
            String pdfPath = pdfGeneratorService.generate(fanfic, chapters);
            fanfic.setPdfPath(pdfPath);
            markStatus(fanfic, FanficStatus.PARSED, null);

            log.info("Successfully parsed fanfic {} ({} chapters)", fanfic.getExternalId(), chapterCount);
        } catch (Exception e) {
            log.error("Failed to parse fanfic {}", fanficId, e);
            markStatus(fanfic, FanficStatus.FAILED, e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
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
