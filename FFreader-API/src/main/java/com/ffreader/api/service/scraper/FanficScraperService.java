package com.ffreader.api.service.scraper;

import com.ffreader.api.exception.FanficScrapeException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Drives a headless browser to load fanfiction.net story pages, then hands
 * the rendered HTML off to {@link FanficPageParser} for extraction.
 */
@Slf4j
@Service
public class FanficScraperService {

    private final WebDriverFactory webDriverFactory;
    private final FanficPageParser parser;

    public FanficScraperService(WebDriverFactory webDriverFactory, FanficPageParser parser) {
        this.webDriverFactory = webDriverFactory;
        this.parser = parser;
    }

    /** Loads chapter 1 of the story and returns the full metadata block plus chapter 1 content. */
    public ScrapedFanficInfo fetchInfo(WebDriver driver, FanficUrl url) {
        Document doc = loadPage(driver, url.toChapterUrl(1));
        ScrapedFanficInfo info = parser.parseInfo(doc);
        int chapterCount = parser.parseChapterCountFromSelect(doc, info.chaptersCount());
        if (chapterCount != info.chaptersCount()) {
            info = new ScrapedFanficInfo(
                    info.externalId(), info.title(), info.description(), info.rating(),
                    info.language(), info.genre(), info.characters(), chapterCount,
                    info.words(), info.reviews(), info.favs(), info.follows(), info.publishedAt()
            );
        }
        return info;
    }

    public ScrapedChapter fetchChapterContent(WebDriver driver, FanficUrl url, int chapterNumber) {
        Document doc = loadPage(driver, url.toChapterUrl(chapterNumber));
        String html = parser.parseChapterContent(doc);
        return new ScrapedChapter(chapterNumber, html);
    }

    public Document currentDocument(WebDriver driver) {
        return Jsoup.parse(driver.getPageSource());
    }

    private Document loadPage(WebDriver driver, String url) {
        log.info("Fetching {}", url);
        try {
            driver.get(url);
            new WebDriverWait(driver, Duration.ofSeconds(20))
                    .until(ExpectedConditions.presenceOfElementLocated(By.id("storytextp")));
        } catch (Exception e) {
            // Log the timeout/error but don't throw an exception
            log.warn("Could not find '#storytextp' within 20 seconds for {}. Returning whatever page source loaded.", url);
        }

        // This will now return the HTML of the current page (e.g., Cloudflare check, 404 page, etc.)
        return Jsoup.parse(driver.getPageSource());
    }
}
