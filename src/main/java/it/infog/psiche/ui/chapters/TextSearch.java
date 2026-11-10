package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.Occurrences;
import it.infog.psiche.markdown.model.FoundPlace;
import it.infog.psiche.markdown.model.TextRange;
import it.infog.psiche.markdown.model.enums.LetterCase;
import javafx.application.Platform;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.TwoDimensional.Bias;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static it.infog.psiche.ui.preview.PreviewPane.FOLLOW_POINT;

/**
 * The places of a text the author looks for in the chapter, found as {@link Repetitions} finds a word: capitals or
 * not, inside longer words too, line breaks however written, never in or across the Markdown of a link or an image, so
 * a link's text and an image's caption are looked in but not their address or file. Each place in view shows in the
 * repetitions' faint red, through {@link PlacesInView}, while the repetitions pause.
 * <p>
 * The author goes from place to place, forward or back, round the chapter's end or start; the place gone to becomes
 * the selection, or, inside a link or an image in Default mode, which a selection takes whole, a mark of the
 * selection's look, with the caret before it. The view moves only when the place is out of it, and then puts it a
 * third of the way down, as the preview follows. As the text changes, the places are found again.
 */
final class TextSearch {

    // The places' look in writer.css, and the place gone to's inside a block.
    private static final String STYLE = "found";
    private static final String CURRENT_STYLE = "found-current";

    private final StyleClassedTextArea area;
    private final Supplier<List<TextRange>> markers;
    private final Supplier<List<TextRange>> blocks;
    private final Repetitions repetitions;
    private final PlacesInView marked;
    private final Highlights current;
    private Runnable changed = () -> {};
    // True while the author searches, from open to close.
    private boolean on;
    private String wanted = "";
    private LetterCase letterCase = LetterCase.IGNORED;
    private List<TextRange> places = List.of();
    // The place gone to, or the nearest after the caret once the text changes; -1 with none.
    private int index = -1;
    private boolean wrapped;
    // Where the author was as the search started, which a longer text looked for starts from again.
    private int origin;
    // True while a search waits for a change to be over, so a change of many parts searches once.
    private boolean pending;

    /**
     * @param markers the Markdown around the links' text and the images' captions, where nothing is found
     * @param blocks  the links and images a selection takes whole, in Default mode; none in Advanced
     */
    TextSearch(StyleClassedTextArea area, Supplier<List<TextRange>> markers, Supplier<List<TextRange>> blocks,
               Repetitions repetitions) {

        this.area = area;
        this.markers = markers;
        this.blocks = blocks;
        this.repetitions = repetitions;
        marked = new PlacesInView(area, STYLE);
        current = new Highlights(area, CURRENT_STYLE);
    }

    /** Runs {@code changed} whenever the places, or the one gone to, change. */
    void setOnChanged(Runnable changed) {

        this.changed = changed;
    }

    /** Starts searching from {@code origin}, a position in the text, the repetitions paused meanwhile. */
    void open(int origin) {

        if (!on)
            repetitions.setPaused(true);
        on = true;
        this.origin = origin;
    }

    /** Stops searching: the places' marks go and the repetitions come back; the selection stays. */
    void close() {

        if (!on)
            return;

        on = false;
        pending = false;
        places = List.of();
        index = -1;
        marked.show(List.of());
        current.show(List.of());
        repetitions.setPaused(false);
    }

    /**
     * Looks for {@code wanted}, its line breaks as {@code \n}, and goes to its first place from where the search
     * started, or round the chapter's end to the first of all.
     */
    void find(String wanted, LetterCase letterCase) {

        this.wanted = wanted;
        this.letterCase = letterCase;
        findPlaces();
        goTo(Occurrences.next(places, origin));
    }

    /** Goes to the next place after the one gone to, or the caret, round the chapter's end. */
    void next() {

        int from = index >= 0 && isAtCurrent() ? places.get(index).end() : area.getSelection().getEnd();
        goTo(Occurrences.next(places, from));
    }

    /** Goes back to the place before the one gone to, or the caret, round the chapter's start. */
    void previous() {

        int from = index >= 0 && isAtCurrent() ? places.get(index).start() : area.getSelection().getStart();
        goTo(Occurrences.previous(places, from));
    }

    /** Finds the places again once the change is over: the one after the caret counts as gone to, unshown. */
    void textChanged() {

        if (!on || pending)
            return;

        pending = true;
        Platform.runLater(() -> {
            if (!pending)
                return;

            pending = false;
            findPlaces();
            current.show(List.of());
            index = Occurrences.next(places, area.getCaretPosition()).map(FoundPlace::index).orElse(-1);
            wrapped = false;
            changed.run();
        });
    }

    /** How many places there are. */
    int count() { return places.size(); }
    /** The place gone to, from 0, or -1 with none. */
    int index() { return index; }
    /** True if going there went round the chapter's end or start. */
    boolean wrapped() { return wrapped; }

    private void findPlaces() {

        places = wanted.isEmpty()
                ? List.of()
                : Occurrences.of(area.getText(), wanted, letterCase, markers.get());
        marked.show(places);
    }

    /** Selects the place {@code found}, or with none clears what shows of the last, and tells it changed. */
    private void goTo(Optional<FoundPlace> found) {

        current.show(List.of());
        index = found.map(FoundPlace::index).orElse(-1);
        wrapped = found.map(FoundPlace::wrapped).orElse(false);
        found.map(place -> places.get(place.index())).ifPresent(this::select);
        changed.run();
    }

    /** Selects {@code place}, or marks it with the caret before the block it lies in, and brings it into view. */
    private void select(TextRange place) {

        Optional<TextRange> block = blocks.get().stream().filter(found -> found.overlaps(place)).findFirst();
        if (block.isPresent()) {
            area.moveTo(block.get().start());
            current.show(List.of(place));
        } else
            area.selectRange(place.start(), place.end());

        int paragraph = area.offsetToPosition(place.start(), Bias.Forward).getMajor();
        if (area.getVisibleParagraphs().isEmpty()
                || paragraph <= area.firstVisibleParToAllParIndex() || paragraph >= area.lastVisibleParToAllParIndex())
            bringToFollowPoint(paragraph);
    }

    /** Scrolls {@code paragraph}, which holds the caret, a third of the way down the view, once it is laid out. */
    private void bringToFollowPoint(int paragraph) {

        area.showParagraphAtTop(paragraph);
        area.layout();
        area.getCaretBounds()
                .map(area::screenToLocal)
                .ifPresent(caret -> area.scrollYBy(caret.getMinY() - area.getHeight() * FOLLOW_POINT));
    }

    /** True if the selection, or the caret before a block, is still on the place gone to. */
    private boolean isAtCurrent() {

        TextRange place = places.get(index);
        return area.getSelection().getStart() == place.start() && area.getSelection().getEnd() == place.end()
                || blocks.get().stream().anyMatch(block -> block.overlaps(place)
                && area.getCaretPosition() == block.start());
    }
}
