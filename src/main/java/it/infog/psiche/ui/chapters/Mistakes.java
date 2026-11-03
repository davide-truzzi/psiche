package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.model.TextRange;
import it.infog.psiche.spelling.ProseWords;
import it.infog.psiche.spelling.SpellChecker;
import it.infog.psiche.spelling.model.Word;
import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.util.Duration;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.PlainTextChange;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The spelling mistakes of the manuscript's text, with their red dots in the margin. They are found a moment after
 * the author stops typing, as the counts are; meanwhile those already found move with the text around them, and one
 * the author is typing into is let go until the next check. The manuscript gives them their dotted underline.
 */
final class Mistakes {

    // Long enough not to check at every key, short enough that a mistake shows as soon as the author pauses.
    private static final Duration CHECK_DELAY = Duration.millis(300);
    // Right of the comments' dots, toward the text, so a comment's dot and a mistake's on one line sit side by side.
    private static final double MARK_OFFSET = 10;

    private final StyleClassedTextArea area;
    private final MarginMarks marks;
    private final Runnable changed;
    private final PauseTransition checkDelay = new PauseTransition(CHECK_DELAY);
    // None while the spelling is not checked: turned off, or no dictionary for the book's language.
    private SpellChecker checker;
    private Set<String> ownWords = Set.of();
    private boolean strict;
    private List<TextRange> ranges = List.of();

    /** @param changed told when a check finds the mistakes anew, so the text can show them */
    Mistakes(StyleClassedTextArea area, Runnable changed) {

        this.area = area;
        this.changed = changed;
        marks = new MarginMarks(area, "mistake-mark", MARK_OFFSET, "spelling mistake", "spelling mistakes");
        checkDelay.setOnFinished(event -> checkNow());
    }

    /** The layer of the mistakes' dots, to stack over the text. */
    Node node() {

        return marks.node();
    }

    /** Where the mistakes are in the text now, in order. */
    List<TextRange> ranges() {

        return ranges;
    }

    /** The mistake at {@code index}, a position in the text, from its first letter to just after its last; if any. */
    Optional<TextRange> at(int index) {

        return ranges.stream().filter(range -> range.start() <= index && index <= range.end()).findFirst();
    }

    /** Up to five words the author may have meant by {@code word}, the likeliest first; none while not checking. */
    List<String> suggestions(String word) {

        return checker == null ? List.of() : checker.suggestions(word);
    }

    /**
     * Checks the text with {@code checker}, knowing {@code ownWords} as right, from now on; with none, the text is not
     * checked and has no mistakes.
     *
     * @param strict true to check the words that look like names too
     */
    void check(SpellChecker checker, Set<String> ownWords, boolean strict) {

        this.checker = checker;
        this.ownWords = Set.copyOf(ownWords);
        this.strict = strict;
        checkNow();
    }

    /**
     * Moves the mistakes with the text after {@code change}, and checks the text again once the author pauses. The
     * manuscript shows the text anew after a change anyway, so it is not told.
     */
    void follow(PlainTextChange change) {

        int at = change.getPosition();
        int removedEnd = at + change.getRemoved().length();
        int shift = change.getNetLength();

        // A mistake the change touches, or joins to other letters, is let go until the check: it is another word now.
        List<TextRange> moved = new ArrayList<>();
        for (TextRange range : ranges) {
            if (range.end() <= at) {
                if (!isWordCharacter(range.end()))
                    moved.add(range);
            } else if (range.start() >= removedEnd) {
                TextRange shifted = new TextRange(range.start() + shift, range.end() + shift);
                if (!isWordCharacter(shifted.start() - 1))
                    moved.add(shifted);
            }
        }
        if (!moved.equals(ranges))
            place(moved);

        if (checker != null)
            checkDelay.playFromStart();
    }

    /** Finds the mistakes of the whole text at once, as when a chapter opens. */
    void checkNow() {

        checkDelay.stop();
        place(checker == null
                ? List.of()
                : checker.mistakes(area.getText(), ownWords, strict).stream().map(Word::range).toList());
        changed.run();
    }

    /** True if the text has, at {@code index}, a character that belongs in a word, as the spell checker reads one. */
    private boolean isWordCharacter(int index) {

        if (index < 0 || index >= area.getLength())
            return false;

        return ProseWords.isWordCharacter(area.getText(index, index + 1).charAt(0));
    }

    private void place(List<TextRange> mistakes) {

        ranges = mistakes;
        marks.setParts(mistakes);
    }
}
