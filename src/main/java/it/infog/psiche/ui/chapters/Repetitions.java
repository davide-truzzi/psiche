package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.Occurrences;
import it.infog.psiche.markdown.model.TextRange;
import javafx.animation.PauseTransition;
import javafx.scene.control.IndexRange;
import javafx.util.Duration;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The other places of the word the author is at, in the paragraphs in view, so they see a repetition as they write
 * it, as Kate shows them: a light help that never moves the text. The word is the selection, exactly as selected, or
 * else the word at the caret, once it rests a moment after typing, a click or a key; it is found capitals or not,
 * inside longer words too, in the whole chapter. Each place in view shows on a faint red, as a selection of its own
 * drawn behind the text, and the status bar shows how many times the word is in the chapter. The places found are
 * kept, so those coming into view show at once as the author scrolls, however fast, to look for them; see
 * {@link PlacesInView}. Image lines and links' addresses are no prose, and a word comes back there as expected:
 * places there neither show nor count, and the author at one finds none.
 * <p>
 * While the author types, {@link FastRepetitions} shows the places near the caret at each key; these take their place
 * once the author pauses. The Preferences switch turns both on and off; both also pause while the author searches the
 * text, whose places would otherwise show twice.
 */
final class Repetitions {

    // As long as the spelling check waits, so both show together once the author pauses.
    private static final Duration FIND_DELAY = Duration.millis(300);
    // Shorter words, such as "il", "la", "the" and "at", come back on every line.
    private static final int MIN_LETTERS = 4;
    // The places' look in writer.css.
    private static final String STYLE = "repeated";

    private final StyleClassedTextArea area;
    private final Supplier<List<TextRange>> notProse;
    private final BiConsumer<String, Integer> counted;
    private final PauseTransition findDelay = new PauseTransition(FIND_DELAY);
    // The word's places in the whole chapter but its own, while it is found; none once the text changes.
    private final PlacesInView places;
    private final FastRepetitions fast;
    private boolean on = true;
    // True while the author searches the text; see TextSearch.
    private boolean paused;

    /**
     * @param notProse the parts of the text that are no prose, as image lines and links' addresses, where a word
     *                 comes back as expected: they are neither looked in nor looked for
     * @param counted  told the word and how many times it is in the chapter, its own place included; none as an empty
     *                 word and 0
     */
    Repetitions(StyleClassedTextArea area, Supplier<List<TextRange>> notProse, BiConsumer<String, Integer> counted) {

        this.area = area;
        this.notProse = notProse;
        this.counted = counted;
        places = new PlacesInView(area, STYLE);
        fast = new FastRepetitions(area, notProse);
        findDelay.setOnFinished(event -> findNow());
        area.selectionProperty().addListener((obs, old, selection) -> findLater());
        area.caretPositionProperty().addListener((obs, old, caret) -> findLater());
    }

    /** Shows the repetitions from now on, or none, as Preferences asks. */
    void setOn(boolean on) {

        this.on = on;
        findNow();
    }

    /** Pauses both checks while the author searches the text, or lets them show again, as they would have. */
    void setPaused(boolean paused) {

        this.paused = paused;
        findNow();
    }

    /**
     * Lets the places go once the text changes, as the word may be another now, until the author pauses; meanwhile,
     * the fast check shows those near the caret if {@code byAuthor}, not an edit of the app's own, as opening a
     * chapter.
     */
    void textChanged(boolean byAuthor) {

        show(List.of(), "", 0);
        if (showing() && byAuthor)
            fast.textChanged();
        findLater();
    }

    private boolean showing() {

        return on && !paused;
    }

    private void findLater() {

        if (showing())
            findDelay.playFromStart();
    }

    /** Finds the places of the word the author is at in the whole chapter, and shows those in view. */
    private void findNow() {

        findDelay.stop();
        fast.clear();
        String text = area.getText();
        List<TextRange> skipped = notProse.get();
        Optional<TextRange> word = showing()
                ? wanted(text).filter(found -> Occurrences.outside(found, skipped))
                : Optional.empty();
        if (word.isEmpty()) {
            show(List.of(), "", 0);
            return;
        }

        TextRange own = word.get();
        String wanted = text.substring(own.start(), own.end());
        List<TextRange> all = Occurrences.of(text, wanted, skipped);

        show(all.stream().filter(range -> !own.overlaps(range)).toList(), wanted, all.size());
    }

    /** The selection, if on one line and not only spaces; with none, the word at the caret, if long enough. */
    private Optional<TextRange> wanted(String text) {

        IndexRange selection = area.getSelection();
        if (selection.getLength() == 0)
            return Occurrences.wordAt(text, area.getCaretPosition(), MIN_LETTERS);

        String selected = text.substring(selection.getStart(), selection.getEnd());
        if (selected.isBlank() || selected.indexOf('\n') >= 0)
            return Optional.empty();

        return Optional.of(new TextRange(selection.getStart(), selection.getEnd()));
    }

    /** Keeps {@code others}, the word's places but its own, marks those in view, and tells the count. */
    private void show(List<TextRange> others, String word, int count) {

        counted.accept(word, count);
        places.show(others);
    }
}
