package com.ffreader.api.service.scraper;

import com.ffreader.api.exception.FanficScrapeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Uses the real parser so each test covers the whole "page source to result" path. */
@ExtendWith(MockitoExtension.class)
class FanficScraperServiceTest {

    private static final FanficUrl URL = new FanficUrl("123", 1, "Story");
    private static final String META = """
            <div id="profile_top">
              <b class="xcontrast_txt">Story</b>
              <span class="xgray xcontrast_txt">Rated: Fiction T - English - Chapters: %d - id: 123</span>
            </div>
            """;

    @Mock WebDriver driver;
    @Mock WebDriverFactory webDriverFactory;

    private FanficScraperService service;

    @BeforeEach
    void setUp() {
        service = new FanficScraperService(webDriverFactory, new FanficPageParser());
    }

    @Test
    void fetchInfoLoadsTheFirstChapter() {
        pageLoads(META.formatted(2));

        ScrapedFanficInfo info = service.fetchInfo(driver, URL);

        verify(driver).get("https://www.fanfiction.net/s/123/1/Story");
        assertThat(info.title()).isEqualTo("Story");
        assertThat(info.chaptersCount()).isEqualTo(2);
    }

    @Test
    void fetchInfoPrefersTheChapterDropdownOverTheMetadata() {
        String dropdown = "<select id=\"chap_select\"><option>1</option><option>2</option><option>3</option></select>";
        pageLoads(META.formatted(1) + dropdown + dropdown);

        ScrapedFanficInfo info = service.fetchInfo(driver, URL);

        assertThat(info.chaptersCount()).isEqualTo(3);
        assertThat(info.title()).isEqualTo("Story");
        assertThat(info.externalId()).isEqualTo("123");
    }

    @Test
    void fetchChapterContentLoadsTheRequestedChapter() {
        pageLoads("<div id=\"storytext\"><p>body</p></div>");

        ScrapedChapter chapter = service.fetchChapterContent(driver, URL, 4);

        verify(driver).get("https://www.fanfiction.net/s/123/4/Story");
        assertThat(chapter.chapterNumber()).isEqualTo(4);
        assertThat(chapter.rawHtml()).contains("<p>body</p>");
    }

    @Test
    void aNavigationFailureStillParsesWhateverLoaded() {
        doThrow(new WebDriverException("timeout")).when(driver).get(any());
        when(driver.getPageSource()).thenReturn("<div id=\"storytext\"><p>partial</p></div>");

        assertThat(service.fetchChapterContent(driver, URL, 1).rawHtml()).contains("partial");
    }

    @Test
    void aBlockedPageSurfacesAsAScrapeError() {
        doThrow(new WebDriverException("timeout")).when(driver).get(any());
        when(driver.getPageSource()).thenReturn("<h1>Just a moment...</h1>");

        assertThatThrownBy(() -> service.fetchChapterContent(driver, URL, 1))
                .isInstanceOf(FanficScrapeException.class);
        assertThatThrownBy(() -> service.fetchInfo(driver, URL))
                .isInstanceOf(FanficScrapeException.class);
    }

    @Test
    void currentDocumentParsesThePageSource() {
        when(driver.getPageSource()).thenReturn("<p id=\"x\">hello</p>");

        assertThat(service.currentDocument(driver).selectFirst("#x").text()).isEqualTo("hello");
    }

    private void pageLoads(String html) {
        when(driver.findElement(By.id("storytextp"))).thenReturn(mock(WebElement.class));
        when(driver.getPageSource()).thenReturn(html);
    }
}
