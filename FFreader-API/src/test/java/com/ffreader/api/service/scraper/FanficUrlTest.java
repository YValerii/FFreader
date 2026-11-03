package com.ffreader.api.service.scraper;

import com.ffreader.api.exception.InvalidFanficUrlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FanficUrlTest {

    @Test
    void parsesAFullStoryLink() {
        assertThat(FanficUrl.parse("https://www.fanfiction.net/s/14585136/3/For-Her"))
                .isEqualTo(new FanficUrl("14585136", 3, "For-Her"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://fanfiction.net/s/123",
            "https://www.fanfiction.net/s/123",
            "https://www.fanfiction.net/s/123/",
            "https://www.fanfiction.net/s/123/1",
            "https://www.fanfiction.net/s/123/1/"
    })
    void defaultsTheChapterAndSlug(String url) {
        assertThat(FanficUrl.parse(url)).isEqualTo(new FanficUrl("123", 1, "story"));
    }

    @Test
    void ignoresSurroundingWhitespace() {
        assertThat(FanficUrl.parse("  https://www.fanfiction.net/s/123/2/Slug  "))
                .isEqualTo(new FanficUrl("123", 2, "Slug"));
    }

    @Test
    void ignoresQueryStringsFragmentsAndExtraPathSegments() {
        assertThat(FanficUrl.parse("https://www.fanfiction.net/s/123/2/Slug?a=b").titleSlug()).isEqualTo("Slug");
        assertThat(FanficUrl.parse("https://www.fanfiction.net/s/123/2/Slug#top").titleSlug()).isEqualTo("Slug");
        assertThat(FanficUrl.parse("https://www.fanfiction.net/s/123/2/Slug/extra").titleSlug()).isEqualTo("Slug");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "not a url",
            "https://example.com/s/123",
            "https://www.fanfiction.net/u/123",
            "https://www.fanfiction.net/s/abc",
            "ftp://www.fanfiction.net/s/123"
    })
    void rejectsOtherUrls(String url) {
        assertThatThrownBy(() -> FanficUrl.parse(url))
                .isInstanceOf(InvalidFanficUrlException.class)
                .hasMessageContaining("Not a recognizable fanfiction.net story URL");
    }

    @Test
    void rebuildsLinksToOtherChapters() {
        FanficUrl url = FanficUrl.parse("http://fanfiction.net/s/123/1/Slug");

        assertThat(url.toChapterUrl(7)).isEqualTo("https://www.fanfiction.net/s/123/7/Slug");
    }

    @Test
    void rejectsChapterNumbersThatDoNotFitAnInt() {
        assertThatThrownBy(() -> FanficUrl.parse("https://www.fanfiction.net/s/123/99999999999/Slug"))
                .isInstanceOf(InvalidFanficUrlException.class);
    }
}
