package com.ffreader.api.service.pdf;

import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An ordered list of fonts where each character is drawn with the first font
 * that has a glyph for it. Characters no font covers are shown as '?'.
 */
final class PdfFontChain {

    /** A piece of text that can be drawn with a single font. */
    record Run(PDFont font, String text) {
    }

    private static final int REPLACEMENT = '?';

    private final List<LazyFont> fonts;
    private final Map<Integer, LazyFont> fontByCodePoint = new HashMap<>();
    private final Map<Integer, Float> widthByCodePoint = new HashMap<>();

    PdfFontChain(List<LazyFont> fonts) {
        this.fonts = fonts;
    }

    /** Splits text into runs, dropping control and invisible formatting characters. */
    List<Run> toRuns(String text) throws IOException {
        List<Run> runs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        PDFont currentFont = null;
        for (int codePoint : text.codePoints().toArray()) {
            if (isInvisible(codePoint)) {
                continue;
            }
            int drawn = displayable(codePoint);
            PDFont font = fontFor(drawn).font();
            if (font != currentFont && currentFont != null) {
                runs.add(new Run(currentFont, current.toString()));
                current.setLength(0);
            }
            currentFont = font;
            current.appendCodePoint(drawn);
        }
        if (currentFont != null) {
            runs.add(new Run(currentFont, current.toString()));
        }
        return runs;
    }

    /** Width in points of one character at the given font size. */
    float width(int codePoint, float fontSize) throws IOException {
        if (isInvisible(codePoint)) {
            return 0;
        }
        Float unitWidth = widthByCodePoint.get(codePoint);
        if (unitWidth == null) {
            int drawn = displayable(codePoint);
            unitWidth = fontFor(drawn).font().getStringWidth(Character.toString(drawn)) / 1000;
            widthByCodePoint.put(codePoint, unitWidth);
        }
        return unitWidth * fontSize;
    }

    float width(String text, float fontSize) throws IOException {
        float total = 0;
        for (int codePoint : text.codePoints().toArray()) {
            total += width(codePoint, fontSize);
        }
        return total;
    }

    private int displayable(int codePoint) throws IOException {
        return findFont(codePoint) != null ? codePoint : REPLACEMENT;
    }

    private LazyFont fontFor(int codePoint) throws IOException {
        LazyFont font = findFont(codePoint);
        return font != null ? font : fonts.getFirst();
    }

    private LazyFont findFont(int codePoint) throws IOException {
        if (fontByCodePoint.containsKey(codePoint)) {
            return fontByCodePoint.get(codePoint);
        }
        LazyFont match = null;
        for (LazyFont font : fonts) {
            if (font.hasGlyph(codePoint)) {
                match = font;
                break;
            }
        }
        fontByCodePoint.put(codePoint, match);
        return match;
    }

    private static boolean isInvisible(int codePoint) {
        return Character.isISOControl(codePoint)
                || Character.getType(codePoint) == Character.FORMAT
                || Character.getType(codePoint) == Character.SURROGATE;
    }

    /**
     * A bundled font that is only read when a character actually needs it, so
     * stories that never use the large CJK fonts don't pay to load them.
     */
    static final class LazyFont {
        private final PDDocument document;
        private final String resourcePath;
        private TrueTypeFont trueTypeFont;
        private PDFont pdfFont;

        LazyFont(PDDocument document, String resourcePath) {
            this.document = document;
            this.resourcePath = resourcePath;
        }

        boolean hasGlyph(int codePoint) throws IOException {
            load();
            return trueTypeFont.getUnicodeCmapLookup().getGlyphId(codePoint) != 0;
        }

        PDFont font() throws IOException {
            load();
            return pdfFont;
        }

        private void load() throws IOException {
            if (pdfFont != null) {
                return;
            }
            try (InputStream in = LazyFont.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    throw new IOException("Missing bundled font: " + resourcePath);
                }
                trueTypeFont = new TTFParser().parse(new RandomAccessReadBuffer(in));
            }
            // With subsetting on, PDFBox closes the font when the document is saved.
            pdfFont = PDType0Font.load(document, trueTypeFont, true);
        }
    }
}
