package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.Chapter;
import javafx.collections.ObservableSet;
import javafx.css.PseudoClass;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.function.Function;

/**
 * A chapter in the chapter list: its title, after a dot that is green while the chapter is as the last revision left
 * it and red once it has changes for the next one, and marked "unsaved" while it has unsaved changes; and its menu.
 */
final class ChapterCell extends ListCell<Chapter> {

    private static final PseudoClass CHANGED = PseudoClass.getPseudoClass("changed");
    private static final String UNCHANGED_TIP = "No changes since the last revision";
    private static final String CHANGED_TIP = "Changed since the last revision.\nCommit to save these changes as a new "
            + "revision.";

    private final Region revisionDot = new Region();
    private final StackPane revisionMark = new StackPane(revisionDot);
    private final Tooltip revisionTip = new Tooltip();
    private final Label title = new Label();
    private final Label unsavedMark = new Label("unsaved");
    private final HBox row = new Row();
    private final ObservableSet<Chapter> unsaved;
    private final ChapterRevisions revisions;
    private final ContextMenu menu;

    /**
     * @param unsaved   the chapters with unsaved changes; the list redraws its cells when the set changes
     * @param revisions which chapters changed since the last revision; the list redraws its cells when that changes
     * @param menu      makes the menu of a cell, for the chapter it shows when the menu opens
     */
    ChapterCell(ObservableSet<Chapter> unsaved, ChapterRevisions revisions,
                Function<ListCell<Chapter>, ContextMenu> menu) {

        this.unsaved = unsaved;
        this.revisions = revisions;
        this.menu = menu.apply(this);

        getStyleClass().add("chapter-cell");
        revisionMark.getStyleClass().add("revision-mark");
        revisionDot.getStyleClass().add("revision-dot");
        title.getStyleClass().add("chapter-title");
        unsavedMark.getStyleClass().add("unsaved-mark");
        Tooltip.install(revisionMark, revisionTip);
        // A hidden "unsaved" leaves no room after the title.
        unsavedMark.managedProperty().bind(unsavedMark.visibleProperty());

        // Right-clicking a chapter shows its menu without selecting it, which would open it.
        addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.SECONDARY)
                event.consume();
        });
    }

    /**
     * The title then "unsaved", where a plain cell has its text, and the revision mark in the cell's padding before
     * them. Laid out apart, the mark cannot move the title by the fraction of a pixel a scaled screen rounds to.
     */
    private final class Row extends HBox {

        Row() {

            super(title, unsavedMark);
            getStyleClass().add("chapter-row");
            revisionMark.setManaged(false);
            getChildren().addFirst(revisionMark);
        }

        @Override
        protected void layoutChildren() {

            super.layoutChildren();
            double width = revisionMark.prefWidth(-1);
            revisionMark.resizeRelocate(-width, 0, width, getHeight());
        }
    }

    @Override
    protected void updateItem(Chapter chapter, boolean empty) {

        super.updateItem(chapter, empty);
        setText(null);
        setGraphic(empty ? null : row);
        setContextMenu(empty ? null : menu);
        if (empty)
            return;

        title.setText(chapter.title());
        unsavedMark.setVisible(unsaved.contains(chapter));

        // Unsaved changes are for the next revision too, as Commit saves them first.
        boolean changed = unsaved.contains(chapter) || revisions.changedSinceLastRevision(chapter);
        revisionMark.setVisible(revisions.hasRevisions());
        revisionDot.pseudoClassStateChanged(CHANGED, changed);
        revisionTip.setText(changed ? CHANGED_TIP : UNCHANGED_TIP);
    }
}
