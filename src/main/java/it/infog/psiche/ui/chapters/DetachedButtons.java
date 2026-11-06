package it.infog.psiche.ui.chapters;

import it.infog.psiche.ui.media.DetachedImages;
import it.infog.psiche.ui.notes.DetachedNotes;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.scene.control.Button;

import java.util.List;

import static it.infog.psiche.ui.common.Controls.button;

/**
 * Detached notes and Detached images, under the chapter list: each button is there only while the book keeps
 * something of its kind for no chapter. Neither is in Git, so both work without it. They are looked for again as the
 * chapters change, and a restored chapter is offered what it had when it was deleted, notes first.
 */
final class DetachedButtons {

    private final DetachedNotes notes;
    private final DetachedImages images;

    DetachedButtons(DetachedNotes notes, DetachedImages images) {

        this.notes = notes;
        this.images = images;
    }

    /** Detached notes above Detached images, each spanning the column. */
    List<Button> buttons() {

        return List.of(shownWhile(button("Detached notes", notes::show), notes.anyProperty()),
                shownWhile(button("Detached images", images::show), images.anyProperty()));
    }

    /** Looks again whether the open book keeps notes or images for none of its chapters. */
    void check() {

        notes.check();
        images.check();
    }

    /** Asks, after {@code chapter} is restored, whether its notes and its images come back too. */
    void offerRestore(String chapter) {

        notes.offerRestore(chapter);
        images.offerRestore(chapter);
    }

    private static Button shownWhile(Button button, ReadOnlyBooleanProperty any) {

        button.visibleProperty().bind(any);
        button.managedProperty().bind(any);
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }
}
