package com.ffreader.api.service.scraper;

import com.ffreader.api.exception.InvalidFanficUrlException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a fanfiction.net story URL of the form:
 * https://www.fanfiction.net/s/{storyId}/{chapter}/{title-slug}
 * and allows rebuilding it for other chapter numbers.
 */
public record FanficUrl(String storyId, int chapter, String titleSlug) {

    private static final Pattern PATTERN =
            Pattern.compile("^https?://(?:www\\.)?fanfiction\\.net/s/(\\d+)(?:/(\\d+))?(?:/([^/?#]*))?.*$");

    public static FanficUrl parse(String url) {
        Matcher m = PATTERN.matcher(url.trim());
        if (!m.matches()) {
            throw new InvalidFanficUrlException(url);
        }
        String storyId = m.group(1);
        int chapter;
        try {
            chapter = m.group(2) != null ? Integer.parseInt(m.group(2)) : 1;
        } catch (NumberFormatException e) {
            throw new InvalidFanficUrlException(url);
        }
        String slug = m.group(3) != null && !m.group(3).isBlank() ? m.group(3) : "story";
        return new FanficUrl(storyId, chapter, slug);
    }

    public String toChapterUrl(int chapterNumber) {
        return "https://www.fanfiction.net/s/" + storyId + "/" + chapterNumber + "/" + titleSlug;
    }
}
