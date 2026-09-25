package com.ffreader.api.service.scraper;

import com.ffreader.api.config.FfreaderProperties;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Creates headless Chrome WebDriver instances configured for scraping.
 * Each parsing job gets its own driver instance so jobs can run fully in
 * parallel on the fanfic-parse-* executor threads; the driver is closed
 * once the job finishes (see FanficScraperService).
 */
@Component
public class WebDriverFactory {

    private final FfreaderProperties properties;

    public WebDriverFactory(FfreaderProperties properties) {
        this.properties = properties;
        WebDriverManager.chromedriver().setup();
    }

    public ChromeDriver newDriver() {
        ChromeOptions options = new ChromeOptions();
        boolean headless = properties.scraper() == null || properties.scraper().headless();
        if (headless) {
            options.addArguments("--headless=new");
        }
        options.addArguments(
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1920,1080",
                "--lang=en-US",
                "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        );

        ChromeDriver driver = new ChromeDriver(options);
        int timeoutSeconds = properties.scraper() != null && properties.scraper().pageLoadTimeoutSeconds() > 0
                ? properties.scraper().pageLoadTimeoutSeconds()
                : 30;
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(timeoutSeconds));
        return driver;
    }
}
