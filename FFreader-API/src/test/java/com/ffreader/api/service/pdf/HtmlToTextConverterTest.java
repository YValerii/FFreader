package com.ffreader.api.service.pdf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlToTextConverterTest {

    private final HtmlToTextConverter converter = new HtmlToTextConverter();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "<p></p>", "<div><p>  </p></div>"})
    void returnsNothingForEmptyContent(String html) {
        assertThat(converter.toPlainText(html)).isEmpty();
    }

    @Test
    void keepsPlainTextAsIs() {
        assertThat(converter.toPlainText("Just text")).isEqualTo("Just text");
    }

    @Test
    void separatesParagraphsWithOneBlankLine() {
        assertThat(converter.toPlainText("<p>one</p><p>two</p>")).isEqualTo("one\n\ntwo");
    }

    @Test
    void treatsLineBreaksAsParagraphBreaks() {
        assertThat(converter.toPlainText("one<br>two")).isEqualTo("one\n\ntwo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"div", "li", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6"})
    void treatsBlockTagsAsParagraphs(String tag) {
        assertThat(converter.toPlainText("<%1$s>one</%1$s><%1$s>two</%1$s>".formatted(tag)))
                .isEqualTo("one\n\ntwo");
    }

    @Test
    void horizontalRulesSeparateParagraphs() {
        assertThat(converter.toPlainText("one<hr>two")).isEqualTo("one\n\ntwo");
    }

    @Test
    void collapsesRunsOfEmptyParagraphs() {
        assertThat(converter.toPlainText("<p>one</p><p></p><p></p><br><br><p>two</p>")).isEqualTo("one\n\ntwo");
    }

    @Test
    void stripsInlineTagsKeepingTheirText() {
        assertThat(converter.toPlainText("<p>a <strong>bold</strong> and <em>italic</em> word</p>"))
                .isEqualTo("a bold and italic word");
    }

    @Test
    void collapsesSourceWhitespaceInsideText() {
        assertThat(converter.toPlainText("<p>  one\n   two\t three </p>")).isEqualTo("one two three");
    }

    @Test
    void decodesEntities() {
        assertThat(converter.toPlainText("<p>Tom &amp; Jerry &mdash; &quot;hi&quot;</p>"))
                .isEqualTo("Tom & Jerry — \"hi\"");
    }

    @Test
    void ignoresScriptsAndStyles() {
        assertThat(converter.toPlainText("<script>alert(1)</script><style>p{}</style><p>text</p>"))
                .isEqualTo("text");
    }

    @Test
    void keepsNonLatinText() {
        assertThat(converter.toPlainText("<p>Привет</p><p>你好</p>")).isEqualTo("Привет\n\n你好");
    }

    @Test
    void whitespaceBetweenBlocksAddsNothing() {
        assertThat(converter.toPlainText("<div>\n  <p>one</p>\n  <p>two</p>\n</div>")).isEqualTo("one\n\ntwo");
    }

    @Test
    void whitespaceAfterALineBreakAddsNoLeadingSpace() {
        assertThat(converter.toPlainText("<b>one</b><br> <i>two</i>")).isEqualTo("one\n\ntwo");
    }

    @Test
    void adjacentInlineTagsWithoutWhitespaceStayJoined() {
        assertThat(converter.toPlainText("<p><b>un</b><i>believable</i></p>")).isEqualTo("unbelievable");
    }

    @Test
    void keepsTheSpaceBetweenAdjacentInlineTags() {
        assertThat(converter.toPlainText("<p><strong>Hello</strong> <em>world</em></p>")).isEqualTo("Hello world");
    }
}
