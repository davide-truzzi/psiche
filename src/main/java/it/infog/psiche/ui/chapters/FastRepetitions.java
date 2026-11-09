package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.Occurrences;
import it.infog.psiche.markdown.model.TextRange;
import javafx.application.Platform;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * The other places of the word the author is typing, in the paragraphs around the caret, found at each key, so a fast
 * writer sees a repetition as they write it, without waiting for {@link Repetitions}, which looks in the whole chapter
 * once they pause and then takes their place. The word is the one at the caret, as it grows, from its second letter;
 * it is found capitals or not, inside longer words too, in four paragraphs: the caret's, two before it and one after,
 * all in view, fewer only if fewer are; the blank lines between them are not the author's paragraphs. Each place
 * shows in a box, as a selection of its own drawn behind the text. Image lines and links' addresses are no prose, and
 * are left out as Repetitions leaves them out.
 */
final class FastRepetitions {

    // From the word's second letter, as it is being typed.
    private static final int MIN_LETTERS = 2;
    // The paragraphs looked in, the caret's among them, and how many of those come before it.
    private static final int PARAGRAPHS = 4;
    private static final int BEFORE = 2;
    // The places' look in writer.css.
    private static final String STYLE = "repeated-fast";

    private final StyleClassedTextArea area;
    private final Supplier<List<TextRange>> notProse;
    private final Highlights places;
    // True while a search waits for the change to be over, so a change of many parts searches once.
    private boolean pending;

    /** @param notProse the parts of the text that are no prose: they are neither looked in nor looked for */
    FastRepetitions(StyleClassedTextArea area, Supplier<List<TextRange>> notProse) {

        this.area = area;
        this.notProse = notProse;
        places = new Highlights(area, STYLE);
    }

    /** Finds the word's places once the author's change is over, and the caret has moved with it. */
    void textChanged() {

        if (pending)
            return;

        pending = true;
        Platform.runLater(this::find);
    }

    /** Shows none. */
    void clear() {

        pending = false;
        places.show(List.of());
    }

    /**
     * The first of the paragraphs looked in, for the caret in paragraph {@code caret} of {@code count} in view, all
     * counted from the first in view: two before the caret's, or as few as keep them all in view.
     */
    static int firstLookedIn(int caret, int count) {

        return Math.clamp(caret - BEFORE, 0, Math.max(0, count - PARAGRAPHS));
    }

    /** Finds the places of the word at the caret in the paragraphs around it, and shows them. */
    private void find() {

        if (!pending)
            return;

        pending = false;
        int caret = area.getCurrentParagraph();
        if (area.getVisibleParagraphs().isEmpty()
                || caret < area.firstVisibleParToAllParIndex() || caret > area.lastVisibleParToAllParIndex()) {
            clear();
            return;
        }

        String text = area.getText();
        List<TextRange> skipped = notProse.get();
        Optional<TextRange> word = Occurrences.wordAt(text, area.getCaretPosition(), MIN_LETTERS)
                .filter(found -> Occurrences.outside(found, skipped));
        if (word.isEmpty()) {
            clear();
            return;
        }

        TextRange own = word.get();
        String wanted = text.substring(own.start(), own.end());
        places.show(Occurrences.of(text, wanted, skipped, lookedIn(caret)).stream()
                .filter(place -> !own.overlaps(place))
                .toList());
    }

    /** The text of the paragraphs looked in around paragraph {@code caret}, which has a word, in view. */
    private TextRange lookedIn(int caret) {

        List<Integer> inView = IntStream.rangeClosed(area.firstVisibleParToAllParIndex(),
                        area.lastVisibleParToAllParIndex())
                .filter(paragraph -> !area.getParagraph(paragraph).getText().isBlank())
                .boxed()
                .toList();
        int first = firstLookedIn(inView.indexOf(caret), inView.size());
        int last = inView.get(Math.min(first + PARAGRAPHS, inView.size()) - 1);

        return new TextRange(area.getAbsolutePosition(inView.get(first), 0),
                area.getAbsolutePosition(last, area.getParagraphLength(last)));
    }
}
