package it.infog.psiche.ui.notes;

import it.infog.psiche.book.model.enums.NotesBoard;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableBooleanValue;

import java.util.EnumMap;
import java.util.Map;

/**
 * How many notes each board holds, the open chapter's and the book's, and whether one carries the Duplicate on Import
 * tag, for the Notes tab's title and the switch's dots: the board on screen tells its own, the other is read for them.
 */
final class BoardCounts {

    private final Map<NotesBoard, IntegerProperty> counts = new EnumMap<>(NotesBoard.class);
    private final Map<NotesBoard, BooleanProperty> duplicates = new EnumMap<>(NotesBoard.class);
    // Made once and kept: the boards hold a binding only weakly, so one made on each call could be collected, and the
    // listeners on it lost with it.
    private final BooleanBinding anyDuplicates;

    BoardCounts() {

        for (NotesBoard board : NotesBoard.values()) {
            counts.put(board, new SimpleIntegerProperty());
            duplicates.put(board, new SimpleBooleanProperty());
        }
        anyDuplicates = duplicates.get(NotesBoard.CHAPTER).or(duplicates.get(NotesBoard.BOOK));
    }

    /** Sets what {@code board} holds: {@code count} notes, and whether one carries the Duplicate on Import tag. */
    void set(NotesBoard board, int count, boolean duplicate) {

        counts.get(board).set(count);
        duplicates.get(board).set(duplicate);
    }

    /** Forgets both boards, as the book closes. */
    void clear() {

        for (NotesBoard board : NotesBoard.values())
            set(board, 0, false);
    }

    /**
     * The tab's title: "Notes (2+3)", the chapter's notes then the book's; "Notes (2)" while the book has none of its
     * own, so an author who never uses them never sees the plus; "Notes" with no note at all.
     */
    StringBinding title() {

        IntegerProperty chapter = counts.get(NotesBoard.CHAPTER);
        IntegerProperty book = counts.get(NotesBoard.BOOK);
        return Bindings.createStringBinding(() -> {
            if (book.get() > 0)
                return "Notes (" + chapter.get() + "+" + book.get() + ")";
            return chapter.get() > 0 ? "Notes (" + chapter.get() + ")" : "Notes";
        }, chapter, book);
    }

    /** True while a note on either board carries the Duplicate on Import tag. */
    BooleanBinding anyDuplicates() {

        return anyDuplicates;
    }

    /** True while a note on {@code board} carries the Duplicate on Import tag. */
    ObservableBooleanValue duplicates(NotesBoard board) {

        return duplicates.get(board);
    }
}
