package com.ffreader.api.service.scraper;

import java.time.Instant;

public record ScrapedFanficInfo(
        String externalId,
        String title,
        String description,
        String rating,
        String language,
        String genre,
        String characters,
        Integer chaptersCount,
        Integer words,
        Integer reviews,
        Integer favs,
        Integer follows,
        Instant publishedAt
) {
}
