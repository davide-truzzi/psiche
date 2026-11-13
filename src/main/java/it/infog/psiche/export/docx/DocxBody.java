package it.infog.psiche.export.docx;

import it.infog.psiche.export.XmlText;
import it.infog.psiche.export.document.DocumentBody;
import it.infog.psiche.export.document.model.Picture;
import it.infog.psiche.export.document.model.enums.Alignment;
import it.infog.psiche.export.document.model.enums.PageStart;
import it.infog.psiche.export.document.model.enums.RowPlace;
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
 * in its last paragraph, so the book's front matter is closed by going back to the paragraph before the first chapter,
 * and each chapter by going back to its last one: every chapter is a section of its own, on a new page, so its
 * footnotes are numbered from 1. A footnote's note is written apart, for {@code footnotes.xml}, its reference in the
 * text.
 */
final class DocxBody implements DocumentBody {

    // The A4 page, its 2.5cm margins and the header 1.4cm from the top, in twentieths of a point.
    private static final String PAGE = """
            <w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1418" w:right="1418" w:bottom="1418" w:left="1418" \
            w:header="794" w:footer="709" w:gutter="0"/>""";
    private static final String HEADER = "<w:headerReference w:type=\"default\" r:id=\"header\"/>";
    private static final String NUMBERED_FROM_1 = "<w:pgNumType w:start=\"1\"/>";
    private static final String NOTES_FROM_1 =
            "<w:footnotePr><w:numStart w:val=\"1\"/><w:numRestart w:val=\"eachSect\"/></w:footnotePr>";
    // A note's paragraph after its first, set in as the first one's text, after its number.
    private static final String NOTE_INDENT = "<w:ind w:left=\"284\" w:hanging=\"0\"/>";
    private static final String NOTE_REFERENCE = "<w:rPr><w:rStyle w:val=\"%s\"/></w:rPr>"
            .formatted(StyleIds.of(Style.FOOTNOTE_ANCHOR));
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

    private final StringBuilder body = new StringBuilder();
    private final StringBuilder notes = new StringBuilder();
    // Where the text goes: the body, or a note being written.
    private StringBuilder xml = body;
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
    // How many bookmarks, drawings and notes are written, each numbered in the document from 1.
    private int bookmarks;
    private int drawings;
    private int notesWritten;
    // How many chapters' sections are closed: the first one numbers its pages from 1.
    private int chapterSections;
    // True from a note's start until its first paragraph, which shows its number.
    private boolean noteStarting;
    // The spans open around a note's reference, and whether the text ended with a space there, back once it closes.
    private Deque<String> spansAroundNote;
    private boolean endedWithSpace;

    @Override
    public void startPage(PageStart start, String bookmark) {

        // A page started but never written on, such as a chapter holding only comments, still gets its paragraph.
        if (pageStart != null) {
            openParagraph(Style.FIRST_PARAGRAPH);
            closeParagraph();
        }

        // The front matter's section, without the header, ends on the page before the first chapter, and a chapter's on
        // the page before the next one.
        if (start == PageStart.FIRST_CHAPTER && lastPropertiesEnd >= 0)
            body.insert(lastPropertiesEnd, section(false, true));
        if (start == PageStart.CHAPTER && lastPropertiesEnd >= 0)
            body.insert(lastPropertiesEnd, section(true, chapterSections++ == 0));

        this.pageStart = start;
        this.bookmark = bookmark;
    }

    @Override
    public void openParagraph(Style style) {

        if (xml == notes) {
            noteParagraph(style);
            return;
        }

        //properties
        xml.append("<w:p><w:pPr><w:pStyle w:val=\"%s\"/>".formatted(StyleIds.of(style)));
        if (pageStart == PageStart.CONTENTS)
            xml.append("<w:pageBreakBefore/>");
        appendListItem();
        lastPropertiesEnd = xml.length();
        xml.append("</w:pPr>");
        pageStart = null;
        endsWithSpace = false;

        //bookmark
        if (bookmark == null)
            return;

        appendBookmark(bookmark);
        bookmark = null;
    }

    @Override
    public void closeParagraph() {

        xml.append("</w:p>\n");
    }

    /** Opens a heading as a paragraph, its bookmark at its start; its style gives it its outline level. */
    @Override
    public void openHeading(Style style, int outlineLevel, String bookmark) {

        openParagraph(style);
        appendBookmark(bookmark);
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

    /** A link to a bookmark of the document, in the same style as a link to the web. */
    @Override
    public void openInternalLink(String bookmark) {

        xml.append("<w:hyperlink w:anchor=\"%s\" w:history=\"1\">".formatted(bookmarkName(bookmark)));
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

    // Until a DOCX draws tables, a table's cells are written as the paragraphs they hold, one after the other.

    @Override
    public void openTable(List<Alignment> alignments) {
    }

    @Override
    public void openRow(RowPlace place) {
    }

    @Override
    public void openCell() {
    }

    @Override
    public void closeCell() {
    }

    @Override
    public void closeRow() {
    }

    @Override
    public void closeTable() {
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

    /** The note's reference in the text, then the note, apart, its text in no span of the text around it. */
    @Override
    public void openNote(int number) {

        notesWritten++;
        xml.append("<w:r>").append(NOTE_REFERENCE).append("<w:footnoteReference w:id=\"%d\"/></w:r>"
                .formatted(notesWritten));

        spansAroundNote = new ArrayDeque<>(spans);
        spans.clear();
        endedWithSpace = endsWithSpace;
        xml = notes;
        xml.append("<w:footnote w:id=\"%d\">".formatted(notesWritten));
        noteStarting = true;
    }

    @Override
    public void closeNote() {

        xml.append("</w:footnote>\n");
        xml = body;
        spans.addAll(spansAroundNote);
        endsWithSpace = endedWithSpace;
    }

    /** The note's number, raised as Word raises a reference; Word does not keep it in step, as it is text. */
    @Override
    public void noteAgain(int number) {

        xml.append("<w:r>").append(NOTE_REFERENCE).append(TEXT_START).append(number).append("</w:t></w:r>");
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

    /**
     * The body written so far, ending with the last section: the manuscript's pages, with the header, from page 1
     * unless an earlier chapter's section starts there.
     */
    String xml() {

        return body + section(true, chapterSections == 0);
    }

    /** The notes of the body, for {@code footnotes.xml}. */
    String notes() {

        return notes.toString();
    }

    /** The lists of the body, for {@code numbering.xml}. */
    String lists() {

        return numbering.xml();
    }

    /** The images and links of the body, for {@code document.xml.rels}. */
    String relationships() {

        return relationships.xml();
    }

    /** A paragraph of a note; its first starts with the note's number, as Word writes it, the others set in. */
    private void noteParagraph(Style style) {

        xml.append("<w:p><w:pPr><w:pStyle w:val=\"%s\"/>%s</w:pPr>"
                .formatted(StyleIds.of(style), noteStarting ? "" : NOTE_INDENT));
        if (noteStarting)
            xml.append("<w:r>").append(NOTE_REFERENCE).append("<w:footnoteRef/></w:r><w:r><w:tab/></w:r>");
        noteStarting = false;
        endsWithSpace = true;
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

    /** Marks the place written next with the bookmark {@code name}, which links lead to. */
    private void appendBookmark(String name) {

        bookmarks++;
        xml.append("<w:bookmarkStart w:id=\"%d\" w:name=\"%s\"/><w:bookmarkEnd w:id=\"%d\"/>"
                .formatted(bookmarks, bookmarkName(name), bookmarks));
    }

    /** Word's name for the bookmark {@code name}, the same each time it is asked for; hidden, as Word's own are. */
    private String bookmarkName(String name) {

        return bookmarkNames.computeIfAbsent(name, given -> "_Bookmark" + (bookmarkNames.size() + 1));
    }

    /**
     * The properties of a section of pages, with or without the header, its pages numbered from 1 or on from the
     * section before; its footnotes are numbered from 1.
     */
    private static String section(boolean header, boolean numberedFrom1) {

        return "<w:sectPr>" + (header ? HEADER : "") + NOTES_FROM_1 + PAGE + (numberedFrom1 ? NUMBERED_FROM_1 : "")
                + "</w:sectPr>";
    }
}
