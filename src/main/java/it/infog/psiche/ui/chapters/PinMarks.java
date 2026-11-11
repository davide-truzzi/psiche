package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.model.TextRange;
import it.infog.psiche.notes.NotePins;
import it.infog.psiche.notes.model.NotePin;
import it.infog.psiche.ui.chapters.MarginMarks.LineActions;
import it.infog.psiche.ui.chapters.MarginMarks.Side;
import javafx.scene.Node;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.PlainTextChange;
import org.fxmisc.richtext.model.TwoDimensional.Bias;

import java.util.AbstractList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The open chapter's pinned notes, each on a paragraph of its own, an editor line: a pin in the right margin beside the
 * paragraph's first line, whose tooltip names the note and whose click shows it. The pins follow their paragraphs as
 * the text changes. A pin whose paragraph is joined to a pinned one, emptied or deleted goes to the nearest free one,
 * as a paragraph holds one pin, and back to its own once Undo brings it back. A whole new text, as after a Revert or a
 * restore from History, has its pins found again by their paragraphs' opening text, and those not found are let go.
 */
final class PinMarks {

    private static final String MARK_STYLE = "pin-mark";
    private static final String ICON = "pin";
    // Right in the middle of the right margin.
    private static final double OFFSET = 0;

    private final StyleClassedTextArea area;
    private final MarginMarks marks;
    // The pins moved off their paragraphs, as they were, by note id, until their paragraphs come back.
    private final Map<String, NotePin> homes = new HashMap<>();
    // Each pinned note's pin as the text has it now, and where its paragraph starts, by note id.
    private Map<String, NotePin> pins = Map.of();
    private Map<String, Integer> starts = Map.of();
    // The pinned notes' ids in the order of their paragraphs, as the marks show them.
    private List<String> order = List.of();
    private Function<String, String> label = id -> "";
    private Consumer<String> clicked = id -> {};
    private BiConsumer<String, Integer> moved = (id, paragraph) -> {};
    private Consumer<Map<String, NotePin>> changed = pins -> {};

    PinMarks(StyleClassedTextArea area) {

        this.area = area;
        marks = new MarginMarks(area, Side.RIGHT, MARK_STYLE, ICON, OFFSET, "pinned note", "pinned notes");
        marks.setLineActions(new LineActions() {
            @Override
            public String tip(int part) {
                return label.apply(order.get(part));
            }

            @Override
            public void clicked(int part) {
                clicked.accept(order.get(part));
            }
        });
    }

    /** The layer of the pins, to stack over the text. */
    Node node() {

        return marks.node();
    }

    /** The pins as the text has them now, by note id. */
    Map<String, NotePin> pins() {

        return pins;
    }

    /** Names a note, by its id, in its pin's tooltip. */
    void setLabel(Function<String, String> label) {

        this.label = label;
    }

    /** Runs {@code clicked} with the note's id when the author clicks its pin. */
    void setOnClicked(Consumer<String> clicked) {

        this.clicked = clicked;
    }

    /** Tells {@code moved} the note's id and its new paragraph when its pin goes to another paragraph than its own. */
    void setOnMoved(BiConsumer<String, Integer> moved) {

        this.moved = moved;
    }

    /** Tells {@code changed} all the pins whenever one of them changes, to be saved. */
    void setOnChanged(Consumer<Map<String, NotePin>> changed) {

        this.changed = changed;
    }

    /** Shows {@code wanted}, the chapter's pins as saved, found again in the text as it is now. */
    void show(Map<String, NotePin> wanted) {

        homes.clear();
        place(NotePins.find(area.getText(), wanted), wanted);
    }

    /** Shows no pin, as another chapter opens, and tells no one. */
    void clear() {

        homes.clear();
        place(Map.of(), Map.of());
    }

    /** The paragraph holding {@code position}, a character of the text. */
    int paragraphAt(int position) {

        return area.offsetToPosition(Math.clamp(position, 0, area.getLength()), Bias.Forward).getMajor();
    }

    /** True if {@code paragraph} can hold a pin: it is not blank. */
    boolean canHold(int paragraph) {

        return !area.getText(paragraph).isBlank();
    }

    /** The note pinned to {@code paragraph}, if any. */
    Optional<String> pinnedTo(int paragraph) {

        return pins.entrySet().stream()
                .filter(entry -> entry.getValue().paragraph() == paragraph)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /** Where the note's paragraph starts in the text, if the note is pinned. */
    OptionalInt start(String id) {

        return starts.containsKey(id) ? OptionalInt.of(starts.get(id)) : OptionalInt.empty();
    }

    /** Pins the note to {@code paragraph}, which the note pinned there before, if any, leaves. */
    void pin(String id, int paragraph) {

        Map<String, NotePin> now = new LinkedHashMap<>(pins);
        pinnedTo(paragraph).ifPresent(other -> {
            now.remove(other);
            homes.remove(other);
        });
        homes.remove(id);
        now.put(id, NotePin.of(paragraph, area.getText(paragraph)));
        place(now, pins);
    }

    void unpin(String id) {

        if (!pins.containsKey(id))
            return;

        Map<String, NotePin> now = new LinkedHashMap<>(pins);
        now.remove(id);
        homes.remove(id);
        place(now, pins);
    }

    /** Moves the pins with {@code change}, the latest change of the text. */
    void follow(PlainTextChange change) {

        if (pins.isEmpty())
            return;

        // A whole new text: its pins are found again, as when the chapter opens.
        Map<String, NotePin> before = pins;
        if (change.getPosition() == 0 && change.getRemoved().length() == area.getLength() - change.getNetLength()) {
            homes.clear();
            place(NotePins.find(area.getText(), before), before);
            return;
        }

        int at = change.getPosition();
        Map<String, Integer> paragraphs = new LinkedHashMap<>();
        Set<String> uprooted = new HashSet<>();
        for (String id : order) {
            int start = starts.get(id);
            int shifted = NotePins.shifted(start, at, change.getRemoved(), change.getInserted());
            paragraphs.put(id, area.offsetToPosition(shifted, Bias.Forward).getMajor());
            if (NotePins.uprooted(start, at, change.getRemoved()))
                uprooted.add(id);
        }
        place(NotePins.settled(lines(), paragraphs, uprooted, before, homes, moved), before);
    }

    /** Keeps {@code now} as the pins, shows them, and tells if they differ from {@code before}. */
    private void place(Map<String, NotePin> now, Map<String, NotePin> before) {

        order = now.keySet().stream()
                .sorted(Comparator.comparingInt((String id) -> now.get(id).paragraph()))
                .toList();
        Map<String, Integer> paragraphStarts = new HashMap<>();
        now.forEach((id, pin) -> paragraphStarts.put(id, area.getAbsolutePosition(pin.paragraph(), 0)));
        pins = Map.copyOf(now);
        starts = paragraphStarts;
        marks.setParts(order.stream().map(id -> new TextRange(starts.get(id), starts.get(id) + 1)).toList());

        if (!now.equals(before))
            changed.accept(pins);
    }

    /** The text's paragraphs, read from the text area as they are asked for. */
    private List<String> lines() {

        return new AbstractList<>() {
            @Override
            public String get(int index) {
                return area.getText(index);
            }

            @Override
            public int size() {
                return area.getParagraphs().size();
            }
        };
    }
}
