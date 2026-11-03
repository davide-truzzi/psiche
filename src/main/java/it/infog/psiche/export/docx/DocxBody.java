package it.infog.psiche.export.docx;

import it.infog.psiche.export.XmlText;
import it.infog.psiche.export.document.DocumentBody;
import it.infog.psiche.export.document.model.Picture;
import it.infog.psiche.export.document.model.enums.PageStart;
import it.infog.psiche.export.document.model.enums.Style;
import it.infog.psiche.export.docx.model.OpenList;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The body of {@code word/document.xml} as it is written, with the lists and relationships it needs.
 * <p>
 * WordprocessingML differs from ODF in three ways that shape this class. Text sits in runs, and a run takes a single
 * character style, so spans are kept open on a stack and each run is styled from it. A list item is a paragraph that
 * names its list's instance in {@link Numbering}. And the pages' layout, with or without the header, ends each section
 * in its last paragraph, so the book's front matter is closed by going back to the paragraph before the first chapter.
 */
final class DocxBody implements DocumentBody {

    // The A4 page, its 2.5cm margins and the header 1.4cm from the top, in twentieths of a point.
    private static final String PAGE = """
            <w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1418" w:right="1418" w:bottom="1418" w:left="1418" \
            w:header="794" w:footer="709" w:gutter="0"/>""";
    private static final String HEADER = "<w:headerReference w:type=\"default\" r:id=\"header\"/>";
    private static final String NUMBERED_FROM_1 = "<w:pgNumType w:start=\"1\"/>";
    // The text runs keep their spaces, which XML would otherwise let a word processor collapse.
    private static final String TEXT_START = "<w:t xml:space=\"preserve\">";
    // What each span's character style looks like, given directly to a run inside another span, which takes one style
    // only. In the order WordprocessingML lists a run's properties, so a style can come twice; as in styles.xml.
    private static final List<Map.Entry<String, String>> DIRECT_LOOKS = List.of(
            Map.entry(StyleIds.of(Style.SOURCE_TEXT),
                    "<w:rFonts w:ascii=\"Liberation Mono\" w:hAnsi=\"Liberation Mono\"/>"),
            Map.entry(StyleIds.of(Style.STRONG_EMPHASIS), "<w:b/>"),
            Map.entry(StyleIds.of(Style.EMPHASIS), "<w:i/>"),
            Map.entry(StyleIds.HYPERLINK, "<w:color w:val=\"0563C1\"/>"),
            Map.entry(StyleIds.of(Style.SOURCE_TEXT), "<w:sz w:val=\"21\"/>"),
            Map.entry(StyleIds.HYPERLINK, "<w:u w:val=\"single\"/>"));

    private final StringBuilder xml = new StringBuilder();
    private final Numbering numbering = new Numbering();
    private final Relationships relationships = new Relationships();

    // The character styles of the spans and link open around the text, the innermost first.
    private final Deque<String> spans = new ArrayDeque<>();
    private final Deque<OpenList> lists = new ArrayDeque<>();
    // True from an item's start until its first paragraph, which shows the bullet or number.
    private boolean itemStarting;
    // True if the paragraph's text so far ends with a space, which a space starting the next text would double.
    private boolean endsWithSpace;

    // The page the next paragraph starts, and the bookmark it carries; null when it simply follows on.
    private PageStart pageStart;
    private String bookmark;
    // Word's names for the bookmarks, by the names they are given: Word only takes letters, digits and underscores.
    private final Map<String, String> bookmarkNames = new HashMap<>();
    // Where the last paragraph's properties end, for a section to end in it; -1 before the first paragraph.
    private int lastPropertiesEnd = -1;
    // How many bookmarks and drawings are written, each numbered in the document from 1.
    private int bookmarks;
    private int drawings;

    @Override
    public void startPage(PageStart start, String bookmark) {

        // A page started but never written on, such as a chapter holding only comments, still gets its paragraph.
        if (pageStart != null) {
            openParagraph(Style.FIRST_PARAGRAPH);
            closeParagraph();
        }

        // The front matter's section, without the header, ends on the page before the first chapter.
        if (start == PageStart.FIRST_CHAPTER && lastPropertiesEnd >= 0)
            xml.insert(lastPropertiesEnd, section(false));

        this.pageStart = start;
        this.bookmark = bookmark;
    }

    @Override
    public void openParagraph(Style style) {

        //properties
        xml.append("<w:p><w:pPr><w:pStyle w:val=\"%s\"/>".formatted(StyleIds.of(style)));
        if (pageStart == PageStart.CONTENTS || pageStart == PageStart.CHAPTER)
            xml.append("<w:pageBreakBefore/>");
        appendListItem();
        lastPropertiesEnd = xml.length();
        xml.append("</w:pPr>");
        pageStart = null;
        endsWithSpace = false;

        //bookmark
        if (bookmark == null)
            return;

        bookmarks++;
        xml.append("<w:bookmarkStart w:id=\"%d\" w:name=\"%s\"/><w:bookmarkEnd w:id=\"%d\"/>"
                .formatted(bookmarks, bookmarkName(bookmark), bookmarks));
        bookmark = null;
    }

    @Override
    public void closeParagraph() {

        xml.append("</w:p>\n");
    }

    /** Opens a heading as a paragraph; its style gives it its outline level. */
    @Override
    public void openHeading(Style style, int outlineLevel) {

        openParagraph(style);
    }

    @Override
    public void closeHeading() {

        closeParagraph();
    }

    @Override
    public void openSpan(Style style) {

        spans.push(StyleIds.of(style));
    }

    @Override
    public void closeSpan() {

        spans.pop();
    }

    @Override
    public void openLink(String address) {

        xml.append("<w:hyperlink r:id=\"%s\" w:history=\"1\">".formatted(relationships.link(address)));
        spans.push(StyleIds.HYPERLINK);
    }

    @Override
    public void closeLink() {

        spans.pop();
        xml.append("</w:hyperlink>");
    }

    @Override
    public void openList(Style style, int start) {

        int level = Math.min(lists.size(), Numbering.DEEPEST_LEVEL);
        lists.push(new OpenList(numbering.add(style, level, start), level));
    }

    @Override
    public void openListItem() {

        itemStarting = true;
    }

    @Override
    public void closeListItem() {

        itemStarting = false;
    }

    @Override
    public void closeList() {

        lists.pop();
    }

    /**
     * Writes {@code text} in a run of the open spans' styles. A DOCX keeps every space, as the ODT does within a text,
     * but where two texts meet, such as around an HTML comment left out, a word processor shows the ODT's spaces as
     * one, so a DOCX drops the second too; a line of code keeps its own.
     */
    @Override
    public void text(String text, boolean lineStart) {

        String shown = !lineStart && endsWithSpace && text.startsWith(" ") ? text.substring(1) : text;
        if (shown.isEmpty())
            return;

        endsWithSpace = shown.endsWith(" ");
        String escaped = XmlText.escape(shown).replace("\t", "</w:t><w:tab/>" + TEXT_START);
        xml.append("<w:r>").append(runProperties()).append(TEXT_START).append(escaped).append("</w:t></w:r>");
    }

    @Override
    public void lineBreak() {

        xml.append("<w:r><w:br/></w:r>");
        endsWithSpace = false;
    }

    @Override
    public void image(Picture picture, String title) {

        drawings++;
        xml.append("<w:r>").append(Drawing.inline(picture, drawings, title, relationships.picture(picture)))
                .append("</w:r>");
    }

    /**
     * The chapter's title, linked to its bookmark. A DOCX shows no page: Word only works out a page field when asked
     * to, and asks the reader first when a file wants it done on opening.
     */
    @Override
    public void contentsLine(String title, String bookmark) {

        openParagraph(Style.CONTENTS_1);
        xml.append("<w:hyperlink w:anchor=\"%s\" w:history=\"1\">".formatted(bookmarkName(bookmark)));
        openSpan(Style.INDEX_LINK);
        text(title, false);
        closeSpan();
        xml.append("</w:hyperlink>");
        closeParagraph();
    }

    /** The body written so far, ending with the last section: the manuscript's pages, with the header, from page 1. */
    String xml() {

        return xml + section(true);
    }

    /** The lists of the body, for {@code numbering.xml}. */
    String lists() {

        return numbering.xml();
    }

    /** The images and links of the body, for {@code document.xml.rels}. */
    String relationships() {

        return relationships.xml();
    }

    /** The properties that start a list item's first paragraph with its bullet or number, or indent a later one. */
    private void appendListItem() {

        OpenList list = lists.peek();
        if (list == null)
            return;

        if (itemStarting)
            xml.append("<w:numPr><w:ilvl w:val=\"%d\"/><w:numId w:val=\"%d\"/></w:numPr>"
                    .formatted(list.level(), list.id()));
        else
            xml.append("<w:ind w:left=\"%d\"/>".formatted(Numbering.indent(list.level())));
        itemStarting = false;
    }

    /**
     * The properties of a run of text: the innermost span's character style, since a run takes one, and directly the
     * look of the spans around it, so bold inside italic is both.
     */
    private String runProperties() {

        if (spans.isEmpty())
            return "";

        List<String> around = spans.stream().skip(1).toList();
        StringBuilder properties = new StringBuilder("<w:rPr><w:rStyle w:val=\"%s\"/>".formatted(spans.peek()));
        for (Map.Entry<String, String> look : DIRECT_LOOKS)
            if (around.contains(look.getKey()))
                properties.append(look.getValue());

        return properties.append("</w:rPr>").toString();
    }

    /** Word's name for the bookmark {@code name}, the same each time it is asked for; hidden, as Word's own are. */
    private String bookmarkName(String name) {

        return bookmarkNames.computeIfAbsent(name, given -> "_Bookmark" + (bookmarkNames.size() + 1));
    }

    /** The properties of a section of pages, with or without the header, numbered from 1. */
    private static String section(boolean header) {

        return "<w:sectPr>" + (header ? HEADER : "") + PAGE + NUMBERED_FROM_1 + "</w:sectPr>";
    }
}
