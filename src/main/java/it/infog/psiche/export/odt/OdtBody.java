package it.infog.psiche.export.odt;

import it.infog.psiche.export.XmlText;
import it.infog.psiche.export.document.DocumentBody;
import it.infog.psiche.export.document.model.Picture;
import it.infog.psiche.export.document.model.enums.PageStart;
import it.infog.psiche.export.document.model.enums.Style;
import it.infog.psiche.export.odt.model.PageStyle;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The body of {@code content.xml} as it is written, with the automatic styles it needs. The first paragraph or heading
 * after {@link #startPage} carries the new page: its page break or page style, and a bookmark that links can lead to.
 */
final class OdtBody implements DocumentBody {

    // What a contents line's page field shows until a word processor works the page out, when it lays the book out.
    private static final String UNKNOWN_PAGE = "?";

    private final StringBuilder xml = new StringBuilder();
    // Each automatic style's name and definition, written once however many paragraphs use it.
    private final Map<String, String> automaticStyles = new LinkedHashMap<>();

    // The page the next paragraph or heading starts, and the bookmark it carries; null when it simply follows on.
    private PageStart pageStart;
    private String bookmark;

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
    public void openHeading(Style style, int outlineLevel) {

        xml.append("<text:h text:style-name=\"%s\" text:outline-level=\"%d\">"
                .formatted(styleName(style), outlineLevel));
        appendBookmark();
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
    public void closeLink() {

        xml.append("</text:a>");
    }

    @Override
    public void openList(Style style) {

        xml.append("<text:list text:style-name=\"%s\">\n".formatted(style.odtName()));
    }

    @Override
    public void openListItem(Integer number) {

        xml.append(number == null ? "<text:list-item>" : "<text:list-item text:start-value=\"%d\">".formatted(number));
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

    @Override
    public void image(Picture picture, String title) {

        xml.append(String.format(Locale.ROOT, """
                        <draw:frame text:anchor-type="as-char" svg:width="%.3fcm" svg:height="%.3fcm" \
                        draw:z-index="0"><draw:image xlink:href="%s" xlink:type="simple" xlink:show="embed" \
                        xlink:actuate="onLoad"/><svg:title>%s</svg:title></draw:frame>""",
                picture.width(), picture.height(), picture.entry(), XmlText.escape(title)));
    }

    /**
     * The chapter's title, then past dot leaders a field holding the page its bookmark is on, which a word processor
     * works out when it lays the book out, and again whenever the text changes.
     */
    @Override
    public void contentsLine(String title, String bookmark) {

        String name = XmlText.escape(bookmark);
        String link = Style.INDEX_LINK.odtName();

        openParagraph(Style.CONTENTS_1);
        xml.append("<text:a xlink:type=\"simple\" xlink:href=\"#%s\" text:style-name=\"%s\" "
                .formatted(name, link) + "text:visited-style-name=\"%s\">".formatted(link));
        text(title, false);
        xml.append("</text:a><text:tab/>");
        xml.append("<text:bookmark-ref text:reference-format=\"page\" text:ref-name=\"%s\">%s</text:bookmark-ref>"
                .formatted(name, UNKNOWN_PAGE));
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

    private void appendBookmark() {

        if (bookmark == null)
            return;

        xml.append("<text:bookmark text:name=\"%s\"/>".formatted(XmlText.escape(bookmark)));
        bookmark = null;
    }
}
