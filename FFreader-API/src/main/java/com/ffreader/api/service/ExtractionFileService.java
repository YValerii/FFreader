package com.ffreader.api.service;

import com.ffreader.api.config.FfreaderProperties;
import com.ffreader.api.exception.FanficScrapeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Persists each scraped chapter to a temporary text file named:
 * {extractionUuid}_{storyName}_{chapterNumber}.txt
 * These files are the intermediate artifact between scraping and PDF assembly.
 */
@Slf4j
@Service
public class ExtractionFileService {

    private final Path extractionDir;

    public ExtractionFileService(FfreaderProperties properties) {
        String configured = properties.storage() != null ? properties.storage().extractionDir() : null;
        this.extractionDir = Path.of(configured != null ? configured : "/tmp/ffreader/extractions");
        try {
            Files.createDirectories(extractionDir);
        } catch (IOException e) {
            throw new FanficScrapeException("Could not create extraction directory: " + extractionDir, e);
        }
    }

    /**
     * @param extractionRunId a UUID identifying this whole parsing run (one per fanfic job)
     * @param storyName       sanitized story title used in the file name
     * @param chapterNumber   1-based chapter number
     * @param rawHtml         the raw HTML content of the chapter
     * @return the file name (not full path) that was written
     */
    public String writeChapter(UUID extractionRunId, String storyName, int chapterNumber, String rawHtml) {
        String safeName = sanitize(storyName);
        String fileName = "%s_%s_%d.txt".formatted(extractionRunId, safeName, chapterNumber);
        Path target = extractionDir.resolve(fileName);
        try {
            Files.writeString(target, rawHtml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new FanficScrapeException("Could not write extraction file: " + target, e);
        }
        log.debug("Wrote extraction file {}", target);
        return fileName;
    }

    public String readChapter(String fileName) {
        Path target = extractionDir.resolve(fileName);
        try {
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new FanficScrapeException("Could not read extraction file: " + target, e);
        }
    }

    public void deleteChapter(String fileName) {
        if (fileName == null) {
            return;
        }
        try {
            Files.deleteIfExists(extractionDir.resolve(fileName));
        } catch (IOException e) {
            log.warn("Could not delete extraction file {}: {}", fileName, e.getMessage());
        }
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "story";
        }
        return name.trim().replaceAll("[^a-zA-Z0-9-_]+", "_");
    }
}
