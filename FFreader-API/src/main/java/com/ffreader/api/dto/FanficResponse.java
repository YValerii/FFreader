package com.ffreader.api.dto;

import com.ffreader.api.domain.Fanfic;
import com.ffreader.api.domain.FanficStatus;

import java.time.Instant;
import java.util.UUID;

public record FanficResponse(
        UUID id,
        String sourceUrl,
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
        Instant publishedAt,
        FanficStatus status,
        String errorMessage,
        boolean downloadReady,
        Instant createdAt,
        Instant updatedAt
) {
    public static FanficResponse from(Fanfic f) {
        return new FanficResponse(
                f.getId(),
                f.getSourceUrl(),
                f.getExternalId(),
                f.getTitle(),
                f.getDescription(),
                f.getRating(),
                f.getLanguage(),
                f.getGenre(),
                f.getCharacters(),
                f.getChaptersCount(),
                f.getWords(),
                f.getReviews(),
                f.getFavs(),
                f.getFollows(),
                f.getPublishedAt(),
                f.getStatus(),
                f.getErrorMessage(),
                f.getStatus() == FanficStatus.PARSED && f.getPdfPath() != null,
                f.getCreatedAt(),
                f.getUpdatedAt()
        );
    }
}
