package com.ffreader.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ffreader")
public record FfreaderProperties(Scraper scraper, Cors cors, Storage storage) {

    public record Scraper(boolean headless, int pageLoadTimeoutSeconds, int poolSize) {
    }

    public record Cors(String allowedOrigins) {
    }

    public record Storage(String extractionDir, String pdfDir) {
    }
}
