package com.ffreader.api.service.pdf;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Strips HTML tags from a chapter's raw markup while keeping paragraph and
 * line breaks intact, so the resulting text reads naturally in the PDF
 * instead of becoming one giant run-on paragraph.
 */
@Component
public class HtmlToTextConverter {

    private static final Set<String> BLOCK_TAGS = Set.of(
            "p", "div", "br", "hr", "li", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6"
    );

    public String toPlainText(String html) {
        Document doc = Jsoup.parse(html == null ? "" : html);
        StringBuilder sb = new StringBuilder();

        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode textNode) {
                    String text = textNode.text();
                    if (!text.isBlank()) {
                        sb.append(text);
                    } else if (!text.isEmpty() && !sb.isEmpty() && !Character.isWhitespace(sb.charAt(sb.length() - 1))) {
                        // Whitespace between inline tags still separates words: <b>Hello</b> <i>world</i>
                        sb.append(' ');
                    }
                } else if (node instanceof Element el && el.tagName().equals("br")) {
                    sb.append('\n');
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element el && BLOCK_TAGS.contains(el.tagName())) {
                    sb.append("\n\n");
                }
            }
        }, doc.body());

        return normalize(sb.toString());
    }

    /** Collapses runs of blank lines and trims trailing whitespace per line. */
    private String normalize(String text) {
        String[] lines = text.split("\n");
        StringBuilder out = new StringBuilder();
        int blankRun = 0;
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                blankRun++;
                if (blankRun <= 1) {
                    out.append('\n');
                }
            } else {
                blankRun = 0;
                out.append(trimmed).append('\n');
            }
        }
        return out.toString().strip();
    }
}
