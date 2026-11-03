package com.ffreader.api.service.scraper;

import com.ffreader.api.config.FfreaderProperties;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Starts the real Chrome on a local page, without touching any website.
 * Requires Google Chrome; run with: mvnw test -Dtest=ChromeDriverIT
 */
class ChromeDriverIT {

    @Test
    void startsChromeAndLeavesNoDriverProcessBehindAfterQuit() {
        FfreaderProperties properties = new FfreaderProperties(
                new FfreaderProperties.Scraper(true, 30, 1), null, null);
        Set<Long> driversBefore = driverProcesses();

        WebDriver driver = new WebDriverFactory(properties).newDriver();
        try {
            driver.get("data:text/html,<div id='storytext'>hello</div>");
            assertThat(driver.findElement(By.id("storytext")).getText()).isEqualTo("hello");
            assertThat(driverProcesses()).hasSizeGreaterThan(driversBefore.size());
        } finally {
            driver.quit();
        }

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(driverProcesses()).isSubsetOf(driversBefore));
    }

    private Set<Long> driverProcesses() {
        return ProcessHandle.allProcesses()
                .filter(process -> process.info().command()
                        .map(command -> command.toLowerCase().contains("chromedriver"))
                        .orElse(false))
                .map(ProcessHandle::pid)
                .collect(Collectors.toSet());
    }
}
