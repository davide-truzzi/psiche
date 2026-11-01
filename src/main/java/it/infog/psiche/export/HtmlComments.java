package it.infog.psiche.export;

import java.util.regex.Pattern;

/**
 * HTML comments, such as {@code <!-- a note -->}, which authors use for notes to themselves in a chapter. The preview
 * hides them as a browser does, so the PDF and ODT exports leave them out too; the Markdown export keeps them.
 */
public final class HtmlComments {

    // A comment left open runs to the end, as it does in a browser.
    private static final Pattern COMMENT = Pattern.compile("<!--.*?(?:-->|\\z)", Pattern.DOTALL);

    private HtmlComments() {
    }

    /** {@code html} without its comments; blank if it held nothing else. */
    public static String strip(String html) {

        return COMMENT.matcher(html).replaceAll("");
    }
}
