package it.infog.psiche.export.odt;

import it.infog.psiche.export.XmlText;
import it.infog.psiche.export.document.DocumentBody;
import it.infog.psiche.export.document.model.Picture;
import it.infog.psiche.export.document.model.enums.PageStart;
import it.infog.psiche.export.document.model.enums.Style;
import it.infog.psiche.export.odt.model.PageStyle;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The body of {@code content.xml} as it is written, with the automatic styles it needs. The first paragraph or heading
 * after {@link #startPage} carries the new page: its page break or page style, and a bookmark that links can lead to.
 */
final class OdtBody implements DocumentBody {

    private final StringBuilder xml = new StringBuilder();
    // Each automatic style's name and definition, written once however many paragraphs use it.
    private final Map<String, String> automaticStyles = new LinkedHashMap<>();

    // The page the next paragraph or heading starts, and the bookmark it carries; null when it simply follows on.
    private PageStart pageStart;
    private String bookmark;
    // The number the list just opened starts at, for its first item; null when it counts from 1, or once written.
    private Integer listStart;
    // How many notes are written, which names each one in the document, and the name of the chapter's note by number.
    private int notes;
    private final Map<Integer, String> noteNames = new HashMap<>();

    @Override
    public void startPage(PageStart start, String bookmark) {

        // A page started but never written on, such as a chapter holding only comments, still gets its paragraph.
        if (pageStart != null) {
            openParagraph(Style.FIRST_PARAGRAPH);
            closeParagraph();
        }

        this.pageStart = start;
        this.bookmark = bookmark;
    }

    @Override
    public void openParagraph(Style style) {

        xml.append("<text:p text:style-name=\"%s\">".formatted(styleName(style)));
        appendBookmark();
    }

    @Override
    public void closeParagraph() {

        xml.append("</text:p>\n");
    }

    @Override
    public void openHeading(Style style, int outlineLevel, String bookmark) {

        xml.append("<text:h text:style-name=\"%s\" text:outline-level=\"%d\">"
                .formatted(styleName(style), outlineLevel));
        appendBookmark();
        appendBookmark(bookmark);
    }

    @Override
    public void closeHeading() {

        xml.append("</text:h>\n");
    }

    @Override
    public void openSpan(Style style) {

        xml.append("<text:span text:style-name=\"%s\">".formatted(style.odtName()));
    }

    @Override
    public void closeSpan() {

        xml.append("</text:span>");
    }

    @Override
    public void openLink(String address) {

        xml.append("<text:a xlink:type=\"simple\" xlink:href=\"%s\">".formatted(XmlText.escape(address)));
    }

    @Override
    public void openInternalLink(String bookmark) {

        openLink("#" + bookmark);
    }

    @Override
    public void closeLink() {

        xml.append("</text:a>");
    }

    @Override
    public void openList(Style style, int start) {

        xml.append("<text:list text:style-name=\"%s\">\n".formatted(style.odtName()));
        listStart = start == 1 ? null : start;
    }

    /** Opens an item; a list's first item gives the number it starts at, unless it is 1. */
    @Override
    public void openListItem() {

        xml.append(listStart == null
                ? "<text:list-item>"
                : "<text:list-item text:start-value=\"%d\">".formatted(listStart));
        listStart = null;
    }

    @Override
    public void closeListItem() {

        xml.append("</text:list-item>\n");
    }

    @Override
    public void closeList() {

        xml.append("</text:list>\n");
    }

    /** Writes {@code text} escaped and with its spaces kept, as {@link OdtText#append} does. */
    @Override
    public void text(String text, boolean lineStart) {

        OdtText.append(xml, text, lineStart);
    }

    @Override
    public void lineBreak() {

        xml.append("<text:line-break/>");
    }

    /**
     * A LibreOffice footnote, holding its note's paragraphs; LibreOffice numbers it, from 1 in each chapter, as the
     * stylesheet's notes configuration says, so its number here is only what shows until it does.
     */
    @Override
    public void openNote(int number) {

        notes++;
        String name = "ftn" + notes;
        noteNames.put(number, name);
        xml.append("<text:note text:id=\"%s\" text:note-class=\"footnote\">".formatted(name))
                .append("<text:note-citation>%d</text:note-citation><text:note-body>".formatted(number));
    }

    @Override
    public void closeNote() {

        xml.append("</text:note-body></text:note>");
    }

    /** A reference to the note's number, which LibreOffice keeps the same as the note's. */
    @Override
    public void noteAgain(int number) {

        xml.append("<text:span text:style-name=\"%s\">".formatted(Style.FOOTNOTE_ANCHOR.odtName()))
                .append("<text:note-ref text:note-class=\"footnote\" text:reference-format=\"text\" "
                        + "text:ref-name=\"%s\">%d</text:note-ref>".formatted(noteNames.get(number), number))
                .append("</text:span>");
    }

    @Override
    public void image(Picture picture, String title) {

        xml.append(String.format(Locale.ROOT, """
                        <draw:frame text:anchor-type="as-char" svg:width="%.3fcm" svg:height="%.3fcm" \
                        draw:z-index="0"><draw:image xlink:href="%s" xlink:type="simple" xlink:show="embed" \
                        xlink:actuate="onLoad"/><svg:title>%s</svg:title></draw:frame>""",
                picture.width(), picture.height(), picture.entry(), XmlText.escape(title)));
    }

    /**
     * The chapter's title, linked to its bookmark. An ODT shows no page, as a DOCX: LibreOffice only works out a page
     * field when asked to, and would show the field's placeholder on opening.
     */
    @Override
    public void contentsLine(String title, String bookmark) {

        String name = XmlText.escape(bookmark);
        String link = Style.INDEX_LINK.odtName();

        openParagraph(Style.CONTENTS_1);
        xml.append("<text:a xlink:type=\"simple\" xlink:href=\"#%s\" text:style-name=\"%s\" "
                .formatted(name, link) + "text:visited-style-name=\"%s\">".formatted(link));
        text(title, false);
        xml.append("</text:a>");
        closeParagraph();
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
            return style.odtName();

        PageStyle page = pageStyle(pageStart);
        String name = page.prefix() + "_" + style.odtName();
        automaticStyles.putIfAbsent(name, automaticStyle(name, style, page));
        pageStart = null;

        return name;
    }

    /** How each kind of page start is written. */
    private static PageStyle pageStyle(PageStart start) {

        return switch (start) {
            case TITLE_PAGE -> new PageStyle("Title_Page", "Front_20_Matter", "");
            case CONTENTS -> new PageStyle("Contents_Page", "Front_20_Matter", "");
            case FIRST_CHAPTER -> new PageStyle("First_Chapter", "Standard", "style:page-number=\"1\"");
            case CHAPTER -> new PageStyle("Chapter", "", "fo:break-before=\"page\"");
        };
    }

    /** The automatic style {@code name}: {@code style} on a new page, for {@code <office:automatic-styles>}. */
    private static String automaticStyle(String name, Style style, PageStyle page) {

        String master = page.masterPage().isEmpty()
                ? ""
                : " style:master-page-name=\"%s\"".formatted(page.masterPage());
        String properties = page.paragraphProperties().isEmpty()
                ? ""
                : "<style:paragraph-properties %s/>".formatted(page.paragraphProperties());

        return """
                <style:style style:name="%s" style:family="paragraph" style:parent-style-name="%s"%s>%s</style:style>
                """.formatted(name, style.odtName(), master, properties);
    }

    /** Marks the paragraph or heading starting the page started last with its bookmark, if it has one. */
    private void appendBookmark() {

        if (bookmark == null)
            return;

        appendBookmark(bookmark);
        bookmark = null;
    }

    /** Marks the place written next with the bookmark {@code name}, which links lead to. */
    private void appendBookmark(String name) {

        xml.append("<text:bookmark text:name=\"%s\"/>".formatted(XmlText.escape(name)));
    }
}
