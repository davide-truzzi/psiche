package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.HtmlComments;
import it.infog.psiche.markdown.model.TextRange;
import it.infog.psiche.spelling.SpellChecker;
import javafx.scene.Node;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.PlainTextChange;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The marks on the manuscript's text: its comments in grey, with a brass dot in the margin on each line in view that
 * has one, and its spelling mistakes, found by {@link Mistakes}, with a dotted red line under them and a red dot
 * beside. They follow the text as it changes. Only the paragraphs whose marks change are restyled: a restyled
 * paragraph is measured again, which can move the view.
 */
final class TextMarks {

    // Right in the middle of the left margin; the spelling mistakes' dots sit beside them.
    private static final double COMMENT_MARK_OFFSET = 0;

    private final StyleClassedTextArea area;
    private final MarginMarks commentMarks;
    private final Mistakes mistakes;
    private List<TextRange> comments = List.of();

    TextMarks(StyleClassedTextArea area) {

        this.area = area;
        commentMarks = new MarginMarks(area, "comment-mark", COMMENT_MARK_OFFSET, "comment", "comments");
        mistakes = new Mistakes(area, this::restyle);
    }

    /** The layers of the comments' and the mistakes' dots, to stack over the text in this order. */
    List<Node> layers() {

        return List.of(commentMarks.node(), mistakes.node());
    }

    /** Finds the comments again after {@code change}, moves the mistakes with the text, and shows both. */
    void follow(PlainTextChange change) {

        findComments();
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

    /** Finds the text's comments, and gives each line in view with one a dot in the margin. */
    private void findComments() {

        String text = area.getText();
        comments = text.isEmpty() ? List.of() : HtmlComments.ranges(text);
        commentMarks.setParts(comments);
    }

    /** Shows the comments in grey and the spelling mistakes with a dotted line under them. */
    private void restyle() {

        StyleSpans<Collection<String>> styles = TextStyles.of(area.getLength(), comments, mistakes.ranges());
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
