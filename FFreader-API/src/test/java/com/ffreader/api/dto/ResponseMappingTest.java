package com.ffreader.api.dto;

import com.ffreader.api.domain.Fanfic;
import com.ffreader.api.domain.FanficStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseMappingTest {

    @Test
    void copiesEveryFanficField() {
        UUID id = UUID.randomUUID();
        Instant published = Instant.ofEpochSecond(1_000);
        Instant updated = Instant.ofEpochSecond(2_000);

        Fanfic fanfic = new Fanfic();
        fanfic.setId(id);
        fanfic.setSourceUrl("url");
        fanfic.setExternalId("123");
        fanfic.setTitle("title");
        fanfic.setDescription("description");
        fanfic.setRating("T");
        fanfic.setLanguage("English");
        fanfic.setGenre("Romance");
        fanfic.setCharacters("A., B.");
        fanfic.setChaptersCount(5);
        fanfic.setLoadedChaptersCount(4);
        fanfic.setWords(1000);
        fanfic.setReviews(1);
        fanfic.setFavs(2);
        fanfic.setFollows(3);
        fanfic.setPublishedAt(published);
        fanfic.setStatus(FanficStatus.FAILED);
        fanfic.setErrorMessage("error");
        fanfic.setUpdatedAt(updated);

        assertThat(FanficResponse.from(fanfic)).isEqualTo(new FanficResponse(
                id, "url", "123", "title", "description", "T", "English", "Romance", "A., B.",
                5, 4, 1000, 1, 2, 3, published, FanficStatus.FAILED, "error", false,
                fanfic.getCreatedAt(), updated));
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING, true, false",
            "PARSING, true, false",
            "FAILED, true, false",
            "PARSED, true, true",
            "PARSED_PARTIALLY, true, true",
            "PARSED, false, false",
            "PARSED_PARTIALLY, false, false"
    })
    void downloadIsReadyOnlyForParsedStoriesWithAPdf(FanficStatus status, boolean hasPdf, boolean expected) {
        Fanfic fanfic = new Fanfic();
        fanfic.setStatus(status);
        fanfic.setPdfPath(hasPdf ? "story.pdf" : null);

        assertThat(FanficResponse.from(fanfic).downloadReady()).isEqualTo(expected);
    }

    @Test
    void wrapsASpringPage() {
        PageResponse<String> response = PageResponse.from(
                new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 5));

        assertThat(response).isEqualTo(new PageResponse<>(List.of("a", "b"), 0, 2, 5, 3, false));
    }
}
