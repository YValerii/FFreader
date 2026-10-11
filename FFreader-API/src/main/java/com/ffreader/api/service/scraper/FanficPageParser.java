package com.ffreader.api.service.scraper;

import com.ffreader.api.exception.FanficScrapeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts story metadata and chapter content from the rendered HTML of a
 * fanfiction.net story page. Kept free of Selenium so it can be unit tested
 * against saved HTML fixtures.
 */
@Component
public class FanficPageParser {

    private static final Pattern NUMBER = Pattern.compile("[0-9,]+");

    /**
     * Parses the metadata block, e.g.:
     * <pre>
     * &lt;b class="xcontrast_txt"&gt;For Her&lt;/b&gt;
     * &lt;div class="xcontrast_txt"&gt;description...&lt;/div&gt;
     * &lt;span class="xgray xcontrast_txt"&gt;Rated: ... - English - Romance/Mystery -
     *   Harry P., Daphne G. - Chapters: 2 - Words: 24,385 - Reviews: 4 - Favs: 9 -
     *   Follows: 14 - Published: &lt;span data-xutime="..."&gt;Aug 22&lt;/span&gt; - id: 14585136&lt;/span&gt;
     * </pre>
     */
    public ScrapedFanficInfo parseInfo(Document doc) {
        Element profile = firstNonNull(doc.selectFirst("#profile_top"), doc);

        Element titleEl = profile.selectFirst("b.xcontrast_txt");
        if (titleEl == null) {
            throw new FanficScrapeException("Could not locate story title (b.xcontrast_txt) on page");
        }
        String title = titleEl.text().trim();

        Element descEl = profile.selectFirst("div.xcontrast_txt[style]");
        String description = descEl != null ? descEl.text().trim() : "";

        Element metaSpan = profile.selectFirst("span.xgray.xcontrast_txt");
        if (metaSpan == null) {
            throw new FanficScrapeException("Could not locate story metadata (span.xgray.xcontrast_txt) on page");
        }

        String rating = extractRating(metaSpan);
        Instant publishedAt = extractPublished(metaSpan);
        String externalId = extractField(metaSpan.text(), "id:");

        List<String> tokens = splitTokens(metaSpan.text());
        // Known bracketed integer fields we can pull by prefix regardless of position
        Integer chapters = extractInt(tokens, "Chapters:");
        Integer words = extractInt(tokens, "Words:");
        Integer reviews = extractInt(tokens, "Reviews:");
        Integer favs = extractInt(tokens, "Favs:");
        Integer follows = extractInt(tokens, "Follows:");

        // Language is always the token right after "Rated: ...".
        String language = tokenAfterPrefix(tokens, "Rated:");

        // Genre / characters sit between language and "Chapters:" (order: Rated, Language, Genre, Characters).
        String genre = null;
        String characters = null;
        int ratedIdx = indexOfPrefix(tokens, "Rated:");
        int chaptersIdx = indexOfPrefix(tokens, "Chapters:");
        if (ratedIdx >= 0 && chaptersIdx > ratedIdx + 1) {
            List<String> between = tokens.subList(ratedIdx + 2, chaptersIdx);
            if (!between.isEmpty()) {
                genre = between.get(0);
            }
            if (between.size() > 1) {
                characters = String.join(" - ", between.subList(1, between.size()));
            }
        }

        if (chapters == null) {
            chapters = 1; // one-shot with no explicit "Chapters:" token
        }

        return new ScrapedFanficInfo(
                externalId, title, description, rating, language, genre, characters,
                chapters, words, reviews, favs, follows, publishedAt
        );
    }

    /** Reads the chapter dropdown (if present) to double check the chapter count. */
    public int parseChapterCountFromSelect(Document doc, int fallback) {
        Elements options = doc.select("#chap_select option");
        return options.isEmpty() ? fallback : options.size()/2;
    }

    /** Extracts the raw HTML of the story body for the currently loaded chapter. */
    public String parseChapterContent(Document doc) {
        Element storyText = doc.selectFirst("#storytext");
        if (storyText == null) {
            throw new FanficScrapeException("Could not locate #storytext content div on page");
        }
        return storyText.html();
    }

    // ---- helpers -------------------------------------------------------

    private Element firstNonNull(Element preferred, Document fallback) {
        return preferred != null ? preferred : fallback;
    }

    private String extractRating(Element metaSpan) {
        Element ratingLink = metaSpan.selectFirst("a[href*=fictionratings]");
        if (ratingLink == null) {
            return null;
        }
        return ratingLink.text().replace("Fiction", "").trim();
    }

    private Instant extractPublished(Element metaSpan) {
        Element timeEl = metaSpan.selectFirst("span[data-xutime]");
        if (timeEl == null) {
            return null;
        }
        try {
            long epochSeconds = Long.parseLong(timeEl.attr("data-xutime").trim());
            return Instant.ofEpochSecond(epochSeconds);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Splits the metadata span's plain text on " - " separators into trimmed tokens. */
    private List<String> splitTokens(String text) {
        List<String> tokens = new ArrayList<>();
        for (String part : text.split(" - ")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    private int indexOfPrefix(List<String> tokens, String prefix) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private String tokenAfterPrefix(List<String> tokens, String prefix) {
        int idx = indexOfPrefix(tokens, prefix);
        return (idx >= 0 && idx + 1 < tokens.size()) ? tokens.get(idx + 1) : null;
    }

    private Integer extractInt(List<String> tokens, String prefix) {
        for (String token : tokens) {
            if (token.startsWith(prefix)) {
                Matcher m = NUMBER.matcher(token);
                if (m.find()) {
                    return Integer.parseInt(m.group().replace(",", ""));
                }
            }
        }
        return null;
    }

    private String extractField(String fullText, String prefix) {
        int idx = fullText.lastIndexOf(prefix);
        if (idx < 0) {
            return null;
        }
        String rest = fullText.substring(idx + prefix.length()).trim();
        Matcher m = NUMBER.matcher(rest);
        return m.find() ? m.group().replace(",", "") : null;
    }
}
