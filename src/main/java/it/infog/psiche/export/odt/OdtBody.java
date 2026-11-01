package it.infog.psiche.export.odt;

import it.infog.psiche.export.odt.model.Picture;
import it.infog.psiche.export.odt.model.enums.PageStart;
import it.infog.psiche.export.odt.model.enums.Style;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The body of {@code content.xml} as it is written, with the automatic styles it needs. Paragraphs and headings are
 * opened here, so that the first one after {@link #startPage} can carry the new page: its page break or page style, and
 * a bookmark that links can lead to.
 */
public final class OdtBody {

    private final StringBuilder xml = new StringBuilder();
    // Each automatic style's name and definition, written once however many paragraphs use it.
    private final Map<String, String> automaticStyles = new LinkedHashMap<>();

    // The page the next paragraph or heading starts, and the bookmark it carries; null when it simply follows on.
    private PageStart pageStart;
    private String bookmark;

    /**
     * Starts a new page with the next paragraph or heading.
     *
     * @param bookmark the name to mark the page's start with, such as a chapter's id; null for none
     */
    void startPage(PageStart start, String bookmark) {

        // A page started but never written on, such as a chapter holding only comments, still gets its paragraph.
        if (pageStart != null) {
            openParagraph(Style.FIRST_PARAGRAPH);
            append("</text:p>\n");
        }

        this.pageStart = start;
        this.bookmark = bookmark;
    }

    /** Opens a paragraph of {@code style}; the caller writes its content and closes it. */
    void openParagraph(Style style) {

        xml.append("<text:p text:style-name=\"%s\">".formatted(styleName(style)));
        appendBookmark();
    }

    /** Opens a heading of {@code style} at {@code outlineLevel}, from 1 to 4; the caller closes it. */
    void openHeading(Style style, int outlineLevel) {

        xml.append("<text:h text:style-name=\"%s\" text:outline-level=\"%d\">"
                .formatted(styleName(style), outlineLevel));
        appendBookmark();
    }

    /** Appends XML as it is. */
    OdtBody append(String text) {

        xml.append(text);
        return this;
    }

    /**
     * Appends {@code picture}, drawn in the line like a large character.
     *
     * @param title the text a screen reader reads for it, such as an image's alt text
     */
    void image(Picture picture, String title) {

        xml.append(String.format(Locale.ROOT, """
                        <draw:frame text:anchor-type="as-char" svg:width="%.3fcm" svg:height="%.3fcm" \
                        draw:z-index="0"><draw:image xlink:href="%s" xlink:type="simple" xlink:show="embed" \
                        xlink:actuate="onLoad"/><svg:title>%s</svg:title></draw:frame>""",
                picture.width(), picture.height(), picture.entry(), OdtText.escape(title)));
    }

    /** Appends {@code text}, escaped and with its spaces kept, as {@link OdtText#append} does. */
    void text(String text, boolean lineStart) {

        OdtText.append(xml, text, lineStart);
    }

    /** The body written so far, for {@code <office:text>}. */
    String xml() {

        return xml.toString();
    }

    /** The automatic styles the page starts need, for {@code <office:automatic-styles>}. */
    String automaticStyles() {

        return String.join("", automaticStyles.values());
    }

    /** {@code style}'s name, or that of its automatic style if this paragraph starts a page. */
    private String styleName(Style style) {

        if (pageStart == null)
            return style.xmlName();

        String name = pageStart.styleName(style);
        automaticStyles.putIfAbsent(name, pageStart.automaticStyle(style));
        pageStart = null;

        return name;
    }

    private void appendBookmark() {

        if (bookmark == null)
            return;

        xml.append("<text:bookmark text:name=\"%s\"/>".formatted(OdtText.escape(bookmark)));
        bookmark = null;
    }
}
