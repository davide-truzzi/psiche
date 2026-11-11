package it.infog.psiche.ui.notes;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * The open chapter's pins, as the editor keeps them on their paragraphs, for the Notes tab: which notes are pinned,
 * and pinning, unpinning and going to a note's paragraph. A paragraph is an editor line; the caret's is the one a note
 * is pinned to.
 */
public interface NotePinning {

    /** How a note is named where its pin is told of: its first line, from its {@code text}, or "Empty note". */
    static String label(String text) {

        return text.strip().lines().findFirst().orElse("Empty note");
    }

    /** True if the note is pinned to a paragraph of the open chapter. */
    boolean isPinned(String id);

    /** True if the caret is on a paragraph that can hold a pin: one that is not blank. */
    boolean canPinHere();

    /** The note pinned to the caret's paragraph, if any. */
    Optional<String> pinnedHere();

    /**
     * Pins the note to the caret's paragraph, which the note pinned there before, if any, leaves.
     *
     * @return the paragraph's number, counted from 1; empty if the caret's paragraph cannot hold a pin
     */
    OptionalInt pinHere(String id);

    /** Unpins the note, as it is unpinned or leaves the chapter. */
    void unpin(String id);

    /** Puts the caret on the note's paragraph, a few lines below the top of the view, and the focus in the text. */
    void goTo(String id);
}
