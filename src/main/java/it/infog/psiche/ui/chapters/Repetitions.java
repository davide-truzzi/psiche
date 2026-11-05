package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.Occurrences;
import it.infog.psiche.markdown.model.TextRange;
import javafx.animation.PauseTransition;
import javafx.scene.control.IndexRange;
import javafx.util.Duration;
import org.fxmisc.richtext.Selection;
import org.fxmisc.richtext.SelectionImpl;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * The other places of the word the author is at, in the paragraphs in view, so they see a repetition as they write
 * it, as Kate shows them: a light help that never moves the text. The word is the selection, exactly as selected, or
 * else the word at the caret, once it rests a moment after typing, a click, a key or a scroll; it is found capitals
 * or not, inside longer words too. Each place shows on a faint red, as a selection of its own drawn behind the text,
 * and the status bar shows how many times the word is in the whole chapter.
 * <p>
 * The places are never styles of the text: restyling a paragraph in view makes RichTextFX measure it again, and its
 * scroll bar can then move the view, far from where the author is.
 */
final class Repetitions {

    // As long as the spelling check waits, so both show together once the author pauses.
    private static final Duration FIND_DELAY = Duration.millis(300);
    // Shorter words, such as "il", "la", "the" and "at", come back on every line.
    private static final int MIN_LETTERS = 4;
    // The places' look in writer.css.
    private static final String STYLE = "repeated";

    private final StyleClassedTextArea area;
    private final BiConsumer<String, Integer> counted;
    private final PauseTransition findDelay = new PauseTransition(FIND_DELAY);
    private boolean on = true;
    private List<TextRange> ranges = List.of();
    private final List<Selection<Collection<String>, String, Collection<String>>> marks = new ArrayList<>();

    /**
     * @param counted told the word and how many times it is in the chapter, its own place included; none as an empty
     *                word and 0
     */
    Repetitions(StyleClassedTextArea area, BiConsumer<String, Integer> counted) {

        this.area = area;
        this.counted = counted;
        findDelay.setOnFinished(event -> findNow());
        area.selectionProperty().addListener((obs, old, selection) -> findLater());
        area.caretPositionProperty().addListener((obs, old, caret) -> findLater());
        // Other paragraphs come into view.
        area.estimatedScrollYProperty().addListener((obs, old, top) -> findLater());
    }

    /** Shows the repetitions from now on, or none, as Preferences asks. */
    void setOn(boolean on) {

        this.on = on;
        findNow();
    }

    /** Lets the places go once the text changes, as the word may be another now, until the author pauses. */
    void textChanged() {

        show(List.of(), "", 0);
        findLater();
    }

    private void findLater() {

        if (on)
            findDelay.playFromStart();
    }

    /** Finds the places of the word the author is at in the paragraphs in view, and shows them. */
    private void findNow() {

        findDelay.stop();
        String text = area.getText();
        Optional<TextRange> word = on ? wanted(text) : Optional.empty();
        if (word.isEmpty()) {
            show(List.of(), "", 0);
            return;
        }

        TextRange own = word.get();
        String wanted = text.substring(own.start(), own.end());
        List<TextRange> all = Occurrences.of(text, wanted);
        TextRange view = view();
        List<TextRange> others = all.stream()
                .filter(range -> range.end() <= own.start() || range.start() >= own.end())
                .filter(range -> range.start() >= view.start() && range.end() <= view.end())
                .toList();

        show(others, wanted, all.size());
    }

    /** The text of the paragraphs in view, even partly; none before the text is laid out. */
    private TextRange view() {

        if (area.getVisibleParagraphs().isEmpty())
            return new TextRange(0, 0);

        int first = area.firstVisibleParToAllParIndex();
        int last = area.lastVisibleParToAllParIndex();
        return new TextRange(area.getAbsolutePosition(first, 0),
                area.getAbsolutePosition(last, area.getParagraphLength(last)));
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

    /** Marks {@code places}, unless they are those marked already, and tells the count. */
    private void show(List<TextRange> places, String word, int count) {

        counted.accept(word, count);
        if (places.equals(ranges))
            return;

        ranges = places;
        // Removing one also disposes of it.
        marks.forEach(area::removeSelection);
        marks.clear();
        for (TextRange place : places) {
            // Named apart: the area keeps its selections in a set ordered by name, where one name is one selection.
            var mark = new SelectionImpl<>(STYLE + marks.size(), area, path -> path.getStyleClass().add(STYLE));
            area.addSelection(mark);
            mark.selectRange(place.start(), place.end());
            marks.add(mark);
        }
    }
}
