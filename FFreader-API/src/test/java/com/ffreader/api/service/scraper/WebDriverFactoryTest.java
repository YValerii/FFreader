package com.ffreader.api.service.scraper;

import com.ffreader.api.config.FfreaderProperties;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/** No browser and no driver download: ChromeDriver and WebDriverManager are both replaced. */
class WebDriverFactoryTest {

    private MockedStatic<WebDriverManager> driverManager;
    private MockedConstruction<ChromeDriver> chromeDrivers;
    private final List<ChromeOptions> options = new ArrayList<>();

    @BeforeEach
    void setUp() {
        driverManager = mockStatic(WebDriverManager.class, RETURNS_DEEP_STUBS);
        chromeDrivers = mockConstruction(ChromeDriver.class,
                withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
                (driver, context) -> options.add((ChromeOptions) context.arguments().getFirst()));
    }

    @AfterEach
    void tearDown() {
        chromeDrivers.close();
        driverManager.close();
    }

    @Test
    void setsUpTheDriverBinaryOnce() {
        new WebDriverFactory(new FfreaderProperties(null, null, null));

        driverManager.verify(WebDriverManager::chromedriver);
    }

    @Test
    void runsHeadlessWhenConfigured() {
        newDriver(new FfreaderProperties.Scraper(true, 30, 1));

        assertThat(chromeArguments()).contains("--headless=new");
    }

    @Test
    void canRunWithAVisibleBrowser() {
        newDriver(new FfreaderProperties.Scraper(false, 30, 1));

        assertThat(chromeArguments()).doesNotContain("--headless=new");
    }

    @Test
    void defaultsToHeadlessWithoutScraperSettings() {
        newDriver(null);

        assertThat(chromeArguments()).contains("--headless=new");
    }

    @Test
    void passesTheFlagsNeededInsideContainers() {
        newDriver(null);

        assertThat(chromeArguments()).contains("--no-sandbox", "--disable-dev-shm-usage");
    }

    @Test
    void appliesTheConfiguredPageLoadTimeout() {
        WebDriver driver = newDriver(new FfreaderProperties.Scraper(true, 45, 1));

        verify(driver.manage().timeouts()).pageLoadTimeout(Duration.ofSeconds(45));
    }

    @Test
    void defaultsThePageLoadTimeoutToThirtySeconds() {
        verify(newDriver(new FfreaderProperties.Scraper(true, 0, 1)).manage().timeouts())
                .pageLoadTimeout(Duration.ofSeconds(30));
        verify(newDriver(null).manage().timeouts()).pageLoadTimeout(Duration.ofSeconds(30));
    }

    @Test
    void everyCallGetsItsOwnBrowser() {
        WebDriverFactory factory = new WebDriverFactory(new FfreaderProperties(null, null, null));

        assertThat(factory.newDriver()).isNotSameAs(factory.newDriver());
    }

    private WebDriver newDriver(FfreaderProperties.Scraper scraper) {
        return new WebDriverFactory(new FfreaderProperties(scraper, null, null)).newDriver();
    }

    @SuppressWarnings("unchecked")
    private List<String> chromeArguments() {
        Map<String, Object> chrome = (Map<String, Object>) options.getLast().asMap().get(ChromeOptions.CAPABILITY);
        return (List<String>) chrome.get("args");
    }
}
