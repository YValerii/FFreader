package com.ffreader.api.service.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfFontChainTest {

    private PDDocument document;
    private PdfFontChain.LazyFont latin;
    private PdfFontChain.LazyFont cjk;
    private PdfFontChain chain;

    @BeforeEach
    void setUp() {
        document = new PDDocument();
        latin = new PdfFontChain.LazyFont(document, "/fonts/DejaVuSans.ttf");
        cjk = new PdfFontChain.LazyFont(document, "/fonts/NotoSansSC-Regular.ttf");
        chain = new PdfFontChain(List.of(latin, cjk));
    }

    @AfterEach
    void tearDown() throws IOException {
        document.close();
    }

    @Test
    void keepsTextInOneRunWhenOneFontCoversIt() throws IOException {
        List<PdfFontChain.Run> runs = chain.toRuns("hello");

        assertThat(runs).containsExactly(new PdfFontChain.Run(latin.font(), "hello"));
    }

    @Test
    void startsANewRunWheneverTheFontChanges() throws IOException {
        List<PdfFontChain.Run> runs = chain.toRuns("ab你好cd");

        assertThat(runs).containsExactly(
                new PdfFontChain.Run(latin.font(), "ab"),
                new PdfFontChain.Run(cjk.font(), "你好"),
                new PdfFontChain.Run(latin.font(), "cd"));
    }

    @Test
    void returnsNoRunsForEmptyOrInvisibleText() throws IOException {
        assertThat(chain.toRuns("")).isEmpty();
        assertThat(chain.toRuns("​\u0007­")).isEmpty();
    }

    @Test
    void dropsInvisibleCharactersFromRuns() throws IOException {
        assertThat(chain.toRuns("a​b")).containsExactly(new PdfFontChain.Run(latin.font(), "ab"));
    }

    @Test
    void dropsLoneSurrogates() throws IOException {
        assertThat(chain.toRuns("a\uD83Db")).containsExactly(new PdfFontChain.Run(latin.font(), "ab"));
    }

    @Test
    void replacesCharactersNoFontHasWithAQuestionMark() throws IOException {
        assertThat(chain.toRuns("a🧿b")).containsExactly(new PdfFontChain.Run(latin.font(), "a?b"));
    }

    @Test
    void invisibleCharactersHaveNoWidth() throws IOException {
        assertThat(chain.width(0x200B, 11f)).isZero();
    }

    @Test
    void widthScalesWithFontSize() throws IOException {
        float small = chain.width('M', 10f);

        assertThat(small).isPositive();
        assertThat(chain.width('M', 20f)).isEqualTo(small * 2);
    }

    @Test
    void textWidthIsTheSumOfItsCharacters() throws IOException {
        assertThat(chain.width("MM", 10f)).isEqualTo(chain.width('M', 10f) * 2);
    }

    @Test
    void unsupportedCharactersAreMeasuredAsTheirReplacement() throws IOException {
        assertThat(chain.width(0x1F9FF, 10f)).isEqualTo(chain.width('?', 10f));
    }

    @Test
    void failsClearlyWhenABundledFontIsMissing() {
        PdfFontChain.LazyFont missing = new PdfFontChain.LazyFont(document, "/fonts/missing.ttf");

        assertThatThrownBy(missing::font)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("/fonts/missing.ttf");
    }
}
