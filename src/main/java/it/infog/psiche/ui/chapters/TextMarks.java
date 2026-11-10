package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.Chapter;
import it.infog.psiche.markdown.Footnotes;
import it.infog.psiche.markdown.HtmlComments;
import it.infog.psiche.markdown.ImageLines;
import it.infog.psiche.markdown.TextBlocks;
import it.infog.psiche.markdown.TextLinks;
import it.infog.psiche.markdown.model.FootnoteMark;
import it.infog.psiche.markdown.model.FootnoteMarks;
import it.infog.psiche.markdown.model.ImageLine;
import it.infog.psiche.markdown.model.TextLink;
import it.infog.psiche.markdown.model.TextRange;
import it.infog.psiche.media.ImageUses;
import it.infog.psiche.spelling.SpellChecker;
import javafx.scene.Node;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.PlainTextChange;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The marks on the manuscript's text: its comments in grey, with a brass dot in the margin on each line in view that
 * has one, its images in green, its links in teal, its images whose file is missing and its links that lead nowhere
 * in orange, its footnotes' marks in amber and the wrong ones in orange, the Markdown's markers around them all, and
 * its spelling mistakes, found by {@link Mistakes}, with a dotted
 * red line under them and a red dot beside. They follow the text as it changes. Only the paragraphs whose marks
 * change are restyled: a restyled paragraph is measured again, which can move the view.
 */
final class TextMarks {

    // Right in the middle of the left margin; the spelling mistakes' dots sit beside them.
    private static final double COMMENT_MARK_OFFSET = 0;

    private final StyleClassedTextArea area;
    private final MarginMarks commentMarks;
    private final Mistakes mistakes;
    private List<TextRange> comments = List.of();
    private List<TextRange> images = List.of();
    private List<TextRange> missingImages = List.of();
    private List<TextRange> links = List.of();
    private List<TextRange> brokenLinks = List.of();
    private List<TextRange> footnotes = List.of();
    private List<TextRange> wrongFootnotes = List.of();
    // The footnotes' marks, right or wrong, in order.
    private List<TextRange> footnoteMarks = List.of();
    // The whole notes, from their opening to their last paragraph's end, and the wrong ones, in order.
    private List<TextRange> notes = List.of();
    private List<TextRange> wrongNotes = List.of();
    // The footnotes' references and whole notes, Default mode's blocks changed only from their dialog, in order.
    private List<TextRange> footnoteBlocks = List.of();
    // The comments' <!-- and -->, the images' and the links' Markdown around their text, and the footnotes' around
    // their labels, in order.
    private List<TextRange> commentMarkers = List.of();
    private List<TextRange> imageAndLinkMarkers = List.of();
    private List<TextRange> footnoteMarkers = List.of();
    // The image lines, the links' addresses and the footnotes' marks, which are no prose, in order.
    private List<TextRange> notProse = List.of();
    // The links, broken or not, the image lines and the footnotes' blocks, in order: the blocks of Default mode.
    private List<TextRange> blocks = List.of();
    private List<TextLink> textLinks = List.of();
    // The open chapter, whose images and links are looked for; null while none is open, and none is missing or broken.
    private Chapter chapter;
    // Each chapter of the book with the anchors of its headings, as saved; null until they are read, and no link to
    // a chapter is broken meanwhile, so none turns orange for the moment it takes to read them.
    private Map<String, Set<String>> bookAnchors;

    TextMarks(StyleClassedTextArea area) {

        this.area = area;
        commentMarks = new MarginMarks(area, "comment-mark", COMMENT_MARK_OFFSET, "comment", "comments");
        mistakes = new Mistakes(area, this::restyle);
    }

    /** The layers of the comments' and the mistakes' dots, to stack over the text in this order. */
    List<Node> layers() {

        return List.of(commentMarks.node(), mistakes.node());
    }

    /**
     * Finds the comments, the images and the links again after {@code change}, moves the mistakes with the text, and
     * shows them all.
     */
    void follow(PlainTextChange change) {

        findComments();
        findFootnotes();
        findImagesAndLinks();
        mistakes.follow(change);
        restyle();
    }

    /**
     * Checks the spelling with {@code checker} from now on, {@code ownWords} being right too, and the words that look
     * like names too if {@code strict}; with none, the text is not checked and shows no mistakes.
     */
    void checkSpelling(SpellChecker checker, Set<String> ownWords, boolean strict) {

        mistakes.check(checker, ownWords, strict);
    }

    /** Finds the mistakes of the whole text at once, as when a chapter opens. */
    void checkSpellingNow() {

        mistakes.checkNow();
    }

    /** The mistake at {@code index}, a position in the text, from its first letter to just after its last; if any. */
    Optional<TextRange> mistakeAt(int index) {

        return mistakes.at(index);
    }

    /** Up to five words the author may have meant by {@code word}, the likeliest first. */
    List<String> suggestions(String word) {

        return mistakes.suggestions(word);
    }

    /**
     * Looks for the images and the links of the text as those of {@code chapter}, now and as the text changes; with
     * {@code null}, as while no chapter is open, none is missing or broken.
     */
    void setChapter(Chapter chapter) {

        this.chapter = chapter;
        findImagesAndLinks();
        restyle();
    }

    /**
     * Checks the links to other chapters against {@code anchors}, each chapter of the book with the anchors of its
     * headings; with {@code null}, as until they are read, none is broken.
     */
    void setBookAnchors(Map<String, Set<String>> anchors) {

        bookAnchors = anchors;
        findImagesAndLinks();
        restyle();
    }

    /**
     * The parts of the text that are no prose, in order: its image lines, caption and file alike, its links'
     * addresses and its footnotes' labels, which repetitions leave out.
     */
    List<TextRange> notProse() { return notProse; }

    /**
     * The Markdown around the links' text and the images' captions, in order: a search finds nothing in or across it,
     * so it leaves the addresses and the files' names as they are.
     */
    List<TextRange> imageAndLinkMarkers() { return imageAndLinkMarkers; }

    /**
     * The text's links, broken or not, its image lines, and its footnotes' references and whole notes, in order:
     * Default mode's blocks; see TextBlocks.
     */
    List<TextRange> blocks() { return blocks; }

    /** True if formatting the selection from {@code start} to {@code end} keeps the blocks whole; see TextBlocks. */
    boolean formattable(int start, int end) {

        return TextBlocks.formattable(inOrder(Stream.concat(images.stream(), footnoteBlocks.stream())), textLinks,
                start, end);
    }

    /**
     * Finds the text's images, in green, and those whose file is not there, in orange; its links, in teal, and those
     * that lead nowhere, in orange; and the parts of both that are no prose, with the footnotes' labels.
     */
    private void findImagesAndLinks() {

        //images
        String text = area.getText();
        List<ImageLine> lines = ImageLines.of(text);
        images = lines.stream().map(ImageLine::range).toList();
        missingImages = chapter == null
                ? List.of()
                : ImageUses.missing(lines, chapter.file().getParent()).stream().map(ImageLine::range).toList();

        //links: the welcome's too, with no chapter open; which links lead nowhere only a chapter's text can tell
        List<TextLink> found = TextLinks.of(text);
        textLinks = found;
        List<TextLink> broken = chapter == null || bookAnchors == null
                ? List.of()
                : TextLinks.broken(text, found, chapter.title(), bookAnchors);
        links = found.stream().filter(link -> !broken.contains(link)).map(TextLink::range).toList();
        brokenLinks = broken.stream().map(TextLink::range).toList();

        //markers, no prose and blocks
        imageAndLinkMarkers = inOrder(Stream.concat(lines.stream().map(ImageLine::markers),
                found.stream().map(TextLink::markers)).flatMap(List::stream));
        notProse = inOrder(Stream.of(images.stream(), found.stream().map(TextLink::address), footnoteMarks.stream())
                .flatMap(Function.identity()));
        blocks = inOrder(Stream.of(images.stream(), found.stream().map(TextLink::range), footnoteBlocks.stream())
                .flatMap(Function.identity()));
    }

    /** Finds the text's comments, and gives each line in view with one a dot in the margin. */
    private void findComments() {

        String text = area.getText();
        comments = text.isEmpty() ? List.of() : HtmlComments.ranges(text);
        commentMarkers = comments.stream().flatMap(comment -> HtmlComments.markers(text, comment).stream()).toList();
        commentMarks.setParts(comments);
    }

    /**
     * Finds the footnotes' references and notes' openings, in amber, and those that are wrong, in orange: a reference
     * to no note, a note no reference leads to, a note of a label an earlier one has.
     */
    private void findFootnotes() {

        FootnoteMarks marks = Footnotes.of(area.getText());
        List<FootnoteMark> wrong = Footnotes.wrong(marks);

        footnotes = inOrder(Stream.concat(marks.references().stream(), marks.notes().stream())
                .filter(mark -> !wrong.contains(mark))
                .map(FootnoteMark::range));
        wrongFootnotes = wrong.stream().map(FootnoteMark::range).toList();
        footnoteMarks = marks.ranges();
        notes = marks.notes().stream().filter(note -> !wrong.contains(note)).map(FootnoteMark::block).toList();
        wrongNotes = marks.notes().stream().filter(wrong::contains).map(FootnoteMark::block).toList();
        footnoteBlocks = marks.blocks();
        footnoteMarkers = inOrder(Stream.of(marks.references(), marks.notes(), marks.missing())
                .flatMap(List::stream)
                .flatMap(mark -> Footnotes.markers(mark).stream()));
    }

    /** Shows each mark in its style; see {@link TextStyles}. */
    private void restyle() {

        SequencedMap<String, List<TextRange>> parts = new LinkedHashMap<>();
        parts.put(TextStyles.COMMENT, comments);
        parts.put(TextStyles.MISTAKE, mistakes.ranges());
        parts.put(TextStyles.IMAGE, images);
        parts.put(TextStyles.MISSING_IMAGE, missingImages);
        parts.put(TextStyles.LINK, links);
        parts.put(TextStyles.BROKEN_LINK, brokenLinks);
        parts.put(TextStyles.FOOTNOTE, footnotes);
        parts.put(TextStyles.WRONG_FOOTNOTE, wrongFootnotes);
        parts.put(TextStyles.NOTE, notes);
        parts.put(TextStyles.WRONG_NOTE, wrongNotes);
        parts.put(TextStyles.MARKER, inOrder(Stream.of(commentMarkers, imageAndLinkMarkers, footnoteMarkers)
                .flatMap(List::stream)));
        StyleSpans<Collection<String>> styles = TextStyles.of(area.getLength(), parts);
        int start = 0;
        for (int paragraph = 0; paragraph < area.getParagraphs().size(); paragraph++) {
            int end = start + area.getParagraphLength(paragraph);
            if (end > start) {
                StyleSpans<Collection<String>> wanted = styles.subView(start, end);
                if (!TextStyles.same(wanted, area.getParagraph(paragraph).getStyleSpans()))
                    area.setStyleSpans(paragraph, 0, wanted);
            }
            // past the line break
            start = end + 1;
        }
    }

    private static List<TextRange> inOrder(Stream<TextRange> ranges) {

        return ranges.sorted(Comparator.comparingInt(TextRange::start)).toList();
    }
}
