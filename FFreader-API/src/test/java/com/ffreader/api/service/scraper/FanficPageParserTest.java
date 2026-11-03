package com.ffreader.api.service.scraper;

import com.ffreader.api.exception.FanficScrapeException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FanficPageParserTest {

    private static final String FULL_META = """
            Rated: <a href="https://www.fictionratings.com/" target="rating">Fiction  T</a>
             - English - Romance/Mystery - Harry P., Daphne G. - Chapters: 2 - Words: 24,385
             - Reviews: 4 - Favs: 9 - Follows: 14
             - Published: <span data-xutime="1661126400">Aug 22</span> - id: 14585136
            """;

    private final FanficPageParser parser = new FanficPageParser();

    @Test
    void parsesAFullMetadataBlock() {
        ScrapedFanficInfo info = parser.parseInfo(page(FULL_META));

        assertThat(info).isEqualTo(new ScrapedFanficInfo(
                "14585136", "For Her", "A description.", "T", "English", "Romance/Mystery",
                "Harry P., Daphne G.", 2, 24385, 4, 9, 14, Instant.ofEpochSecond(1661126400)));
    }

    @Test
    void keepsThePlusInRatings() {
        String meta = """
                Rated: <a href="https://www.fictionratings.com/">Fiction  K+</a> - English - Chapters: 1 - id: 1
                """;

        assertThat(parser.parseInfo(page(meta)).rating()).isEqualTo("K+");
    }

    @Test
    void fallsBackToTheWholeDocumentWithoutAProfileBlock() {
        Document doc = Jsoup.parse("""
                <b class="xcontrast_txt">Loose</b>
                <span class="xgray xcontrast_txt">Rated: Fiction T - English - id: 5</span>
                """);

        ScrapedFanficInfo info = parser.parseInfo(doc);

        assertThat(info.title()).isEqualTo("Loose");
        assertThat(info.externalId()).isEqualTo("5");
    }

    @Test
    void treatsAStoryWithoutAChaptersTokenAsAOneShot() {
        ScrapedFanficInfo info = parser.parseInfo(page("Rated: Fiction T - English - Words: 100 - id: 1"));

        assertThat(info.chaptersCount()).isEqualTo(1);
    }

    @Test
    void leavesOptionalFieldsEmptyWhenTheyAreMissing() {
        ScrapedFanficInfo info = parser.parseInfo(page("Rated: Fiction T - English - Chapters: 1"));

        assertThat(info.rating()).isNull();
        assertThat(info.words()).isNull();
        assertThat(info.reviews()).isNull();
        assertThat(info.favs()).isNull();
        assertThat(info.follows()).isNull();
        assertThat(info.publishedAt()).isNull();
        assertThat(info.externalId()).isNull();
        assertThat(info.genre()).isNull();
        assertThat(info.characters()).isNull();
    }

    @Test
    void ignoresAnUnparseablePublishedDate() {
        ScrapedFanficInfo info = parser.parseInfo(
                page("Rated: Fiction T - English - Published: <span data-xutime=\"soon\">x</span> - id: 1"));

        assertThat(info.publishedAt()).isNull();
    }

    @Test
    void ignoresAFieldPrefixWithoutANumber() {
        assertThat(parser.parseInfo(page("Rated: Fiction T - English - Words: lots - id: none")).words()).isNull();
    }

    @Test
    void allowsAMissingDescription() {
        Document doc = Jsoup.parse("""
                <div id="profile_top">
                  <b class="xcontrast_txt">Title</b>
                  <span class="xgray xcontrast_txt">Rated: Fiction T - English - id: 1</span>
                </div>
                """);

        assertThat(parser.parseInfo(doc).description()).isEmpty();
    }

    @Test
    void failsWhenTheTitleIsMissing() {
        // e.g. a Cloudflare challenge or error page
        assertThatThrownBy(() -> parser.parseInfo(Jsoup.parse("<h1>Just a moment...</h1>")))
                .isInstanceOf(FanficScrapeException.class)
                .hasMessageContaining("title");
    }

    @Test
    void failsWhenTheMetadataIsMissing() {
        Document doc = Jsoup.parse("<div id=\"profile_top\"><b class=\"xcontrast_txt\">Title</b></div>");

        assertThatThrownBy(() -> parser.parseInfo(doc))
                .isInstanceOf(FanficScrapeException.class)
                .hasMessageContaining("metadata");
    }

    @Test
    void countsChaptersFromTheTwoDropdownsOnThePage() {
        Document doc = Jsoup.parse(select(3) + "<p>story</p>" + select(3));

        assertThat(parser.parseChapterCountFromSelect(doc, 99)).isEqualTo(3);
    }

    @Test
    void usesTheFallbackWithoutADropdown() {
        assertThat(parser.parseChapterCountFromSelect(Jsoup.parse("<p>one-shot</p>"), 7)).isEqualTo(7);
    }

    @Test
    void returnsTheChapterHtml() {
        Document doc = Jsoup.parse("<div id=\"storytext\"><p>one</p><p>two</p></div>");

        assertThat(parser.parseChapterContent(doc)).contains("<p>one</p>", "<p>two</p>");
    }

    @Test
    void failsWhenTheChapterContentIsMissing() {
        assertThatThrownBy(() -> parser.parseChapterContent(Jsoup.parse("<p>nope</p>")))
                .isInstanceOf(FanficScrapeException.class)
                .hasMessageContaining("#storytext");
    }

    @Test
    void readsGenreAndCharactersOfOneShots() {
        ScrapedFanficInfo info = parser.parseInfo(
                page("Rated: Fiction T - English - Romance - Harry P. - Words: 100 - id: 1"));

        assertThat(info.genre()).isEqualTo("Romance");
        assertThat(info.characters()).isEqualTo("Harry P.");
    }

    @Test
    void doesNotMistakeCharactersForGenre() {
        ScrapedFanficInfo info = parser.parseInfo(
                page("Rated: Fiction T - English - Harry P., Daphne G. - Chapters: 2 - id: 1"));

        assertThat(info.genre()).isNull();
        assertThat(info.characters()).isEqualTo("Harry P., Daphne G.");
    }

    @Test
    void countsChaptersCorrectlyWithASingleDropdown() {
        assertThat(parser.parseChapterCountFromSelect(Jsoup.parse(select(1)), 99)).isEqualTo(1);
        assertThat(parser.parseChapterCountFromSelect(Jsoup.parse(select(5)), 99)).isEqualTo(5);
    }

    @Test
    void recognisesCombinedAndHyphenatedGenres() {
        assertThat(genreOf("Hurt/Comfort")).isEqualTo("Hurt/Comfort");
        assertThat(genreOf("Romance/Hurt/Comfort")).isEqualTo("Romance/Hurt/Comfort");
        assertThat(genreOf("Sci-Fi/Adventure")).isEqualTo("Sci-Fi/Adventure");
    }

    @Test
    void readsCharactersOfAOneShotWithoutGenre() {
        ScrapedFanficInfo info = parser.parseInfo(page("Rated: Fiction T - English - Harry P. - Words: 100 - id: 1"));

        assertThat(info.genre()).isNull();
        assertThat(info.characters()).isEqualTo("Harry P.");
    }

    @Test
    void stopsAtWhicheverLabelledFieldComesFirst() {
        ScrapedFanficInfo info = parser.parseInfo(page("Rated: Fiction T - English - Drama - Reviews: 3 - id: 1"));

        assertThat(info.genre()).isEqualTo("Drama");
        assertThat(info.characters()).isNull();
    }

    @Test
    void copesWithMetadataThatEndsAfterTheRating() {
        ScrapedFanficInfo info = parser.parseInfo(page("Rated: Fiction T"));

        assertThat(info.language()).isNull();
        assertThat(info.genre()).isNull();
        assertThat(info.characters()).isNull();
    }

    private String genreOf(String genre) {
        return parser.parseInfo(page("Rated: Fiction T - English - " + genre + " - Chapters: 1 - id: 1")).genre();
    }

    private static Document page(String meta) {
        return Jsoup.parse("""
                <div id="profile_top">
                  <b class="xcontrast_txt">For Her</b>
                  <div class="xcontrast_txt" style="margin-top:2px">A description.</div>
                  <span class="xgray xcontrast_txt">%s</span>
                </div>
                """.formatted(meta));
    }

    private static String select(int options) {
        StringBuilder html = new StringBuilder("<select id=\"chap_select\">");
        for (int i = 1; i <= options; i++) {
            html.append("<option value=\"").append(i).append("\">").append(i).append("</option>");
        }
        return html.append("</select>").toString();
    }
}
