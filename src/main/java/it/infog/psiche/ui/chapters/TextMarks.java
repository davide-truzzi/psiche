package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.HtmlComments;
import it.infog.psiche.markdown.model.ImageLine;
import it.infog.psiche.markdown.model.TextRange;
import it.infog.psiche.media.ImageUses;
import it.infog.psiche.spelling.SpellChecker;
import javafx.scene.Node;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.PlainTextChange;
import org.fxmisc.richtext.model.StyleSpans;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The marks on the manuscript's text: its comments in grey, with a brass dot in the margin on each line in view that
 * has one, its images whose file is missing in orange, and its spelling mistakes, found by {@link Mistakes}, with a
 * dotted red line under them and a red dot beside. They follow the text as it changes. Only the paragraphs whose
 * marks change are restyled: a restyled paragraph is measured again, which can move the view.
 */
final class TextMarks {

    // Right in the middle of the left margin; the spelling mistakes' dots sit beside them.
    private static final double COMMENT_MARK_OFFSET = 0;

    private final StyleClassedTextArea area;
    private final MarginMarks commentMarks;
    private final Mistakes mistakes;
    private List<TextRange> comments = List.of();
    private List<TextRange> missingImages = List.of();
    // Where the open chapter's image links are written from; null while no chapter is open, and no image is missing.
    private Path chaptersDir;

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
     * Finds the comments and the missing images again after {@code change}, moves the mistakes with the text, and shows
     * them all.
     */
    void follow(PlainTextChange change) {

        findComments();
        findMissingImages();
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
     * Looks for the images of the text in {@code chaptersDir}, where the open chapter's file is, now and as the text
     * changes; with {@code null}, as while no chapter is open, none is missing.
     */
    void setChaptersDir(Path chaptersDir) {

        this.chaptersDir = chaptersDir;
        findMissingImages();
        restyle();
    }

    /** Finds the text's images whose file is not there, which show in orange. */
    private void findMissingImages() {

        missingImages = chaptersDir == null
                ? List.of()
                : ImageUses.missing(area.getText(), chaptersDir).stream().map(ImageLine::range).toList();
    }

    /** Finds the text's comments, and gives each line in view with one a dot in the margin. */
    private void findComments() {

        String text = area.getText();
        comments = text.isEmpty() ? List.of() : HtmlComments.ranges(text);
        commentMarks.setParts(comments);
    }

    /** Shows the comments in grey, the spelling mistakes with a dotted line under them, missing images in orange. */
    private void restyle() {

        StyleSpans<Collection<String>> styles = TextStyles.of(area.getLength(), comments, mistakes.ranges(),
                missingImages);
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
}
