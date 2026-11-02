package it.infog.psiche.ui.notes;

import it.infog.psiche.notes.model.Note;
import it.infog.psiche.notes.model.NoteLayout;
import it.infog.psiche.notes.model.enums.NoteColor;
import it.infog.psiche.ui.common.Icons;

import javafx.animation.PauseTransition;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static it.infog.psiche.ui.common.Controls.editingItems;
import static it.infog.psiche.ui.common.Controls.hSpacer;
import static it.infog.psiche.ui.common.Controls.menuItem;

/**
 * A note on its chapter's board, drawn as a post-it: a toolbar at the top, a darker tone of the note's colour, to drag
 * it by, with a paperclip that opens the file menu; the text typed in place; and a corner to resize it from. Pressing
 * anywhere on it brings it to the front, so the note dragged last lies over the others. Its colour dot opens a row of
 * swatches. Under the text, the file it carries; a file dropped on it takes the place of the one it had. A right-click
 * offers what fits: the file's actions, the colours, Delete. It tells its {@link Board} what changed: the text a moment
 * after the author stops typing, or on leaving it, and its place, size, colour and stacking order once they settle.
 */
public final class NoteCard extends VBox {

    /** What a card asks of the board it lies on. */
    public interface Board {

        /** Puts {@code card} over every other note, and keeps the new stacking order. */
        void bringToFront(NoteCard card);

        /** Keeps the card's place, size, colour and stacking order, as {@link NoteCard#noteLayout()} gives them. */
        void layoutChanged(NoteCard card);

        void textChanged(NoteCard card, String text);

        /** Moves the note to Detached notes. */
        void delete(NoteCard card);

        /** Clears the Duplicate on Import tag: the author keeps both versions. */
        void clearDuplicateTag(NoteCard card);

        /** Copies {@code file} into the note, in place of the file it carried. */
        void attach(NoteCard card, Path file);

        /** Asks the author for a file, and copies it into the note. */
        void chooseFile(NoteCard card);

        /** Copies the file, or the image, on the clipboard into the note. */
        void paste(NoteCard card);

        /** Opens the note's file in the system's default program. */
        void openFile(NoteCard card);

        /** Opens the note's folder, where its file is, in the system's file manager. */
        void showInFolder(NoteCard card);

        /** Takes the file away from the note, to Detached notes. */
        void removeFile(NoteCard card);

        /** Shows {@code items} as the board's one menu, closing any other, at a point on the screen. */
        void showMenu(NoteCard card, List<MenuItem> items, double screenX, double screenY);
    }

    static final double MIN_WIDTH = 140;
    static final double MIN_HEIGHT = 100;
    static final double MAX_WIDTH = 600;
    static final double MAX_HEIGHT = 600;

    private static final Duration SAVE_DELAY = Duration.seconds(1);
    private static final double TEXT_MIN_HEIGHT = 32;
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final String COLOR_STYLE = "note-%s";
    private static final String DUPLICATE_STYLE = "note-duplicate";

    private final String id;
    private final Board board;
    private final TextArea text = new TextArea();
    private final PauseTransition saveDelay = new PauseTransition(SAVE_DELAY);
    private final StackPane band = new StackPane();
    private final ColorSwatches swatches = new ColorSwatches(this::recolor);
    private final CardDrag drag;
    private final CardFileSlot fileSlot;
    private int order;
    private NoteColor color;
    private boolean duplicateOnImport;

    public NoteCard(Note note, Board board) {

        this.id = note.id();
        this.board = board;
        this.order = note.layout().order();
        this.drag = new CardDrag(this, () -> board.layoutChanged(this));
        this.fileSlot = new CardFileSlot(() -> board.openFile(this));

        text.setText(note.text());
        text.setWrapText(true);
        text.setPromptText("Write a note…");
        text.getStyleClass().add("note-text");
        VBox.setVgrow(text, Priority.ALWAYS);

        //text saved a moment after the author stops typing, and at once on leaving the note
        saveDelay.setOnFinished(event -> board.textChanged(this, text.getText()));
        text.textProperty().addListener((obs, old, value) -> saveDelay.playFromStart());
        text.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused)
                flushText();
        });

        band.getChildren().add(duplicateBand());
        band.getStyleClass().add("note-band");
        drag.moveBy(band);
        setDuplicateOnImport(note.duplicateOnImport());

        setAttachment(note.attachment());

        // The text takes the room the file leaves; in a note made small, the text gives way first, then the file.
        text.setMinHeight(TEXT_MIN_HEIGHT);
        text.setPrefHeight(TEXT_MIN_HEIGHT);

        getChildren().addAll(band, topStrip(), swatches, text, fileSlot, resizeCorner());
        getStyleClass().add("note-card");
        setColor(note.layout().color());

        // A press anywhere, the text included, brings the note to the front without taking the press from the text.
        addEventFilter(MouseEvent.MOUSE_PRESSED, event -> board.bringToFront(this));

        // The text offers editing, the rest of the card the note's actions, both in the board's one menu, so a menu
        // opened on the text closes one opened on the strip, and the other way round.
        text.setOnContextMenuRequested(event -> {
            board.showMenu(this, editingItems(text), event.getScreenX(), event.getScreenY());
            event.consume();
        });
        setOnContextMenuRequested(event -> {
            board.showMenu(this, menuItems(), event.getScreenX(), event.getScreenY());
            event.consume();
        });

        // One file dropped on the note is copied into it; several go to the board, which makes a note of each.
        setOnDragOver(event -> {
            if (event.getDragboard().hasFiles() && event.getDragboard().getFiles().size() == 1) {
                event.acceptTransferModes(TransferMode.COPY);
                event.consume();
            }
        });
        setOnDragDropped(event -> {
            if (!event.getDragboard().hasFiles() || event.getDragboard().getFiles().size() != 1)
                return;

            board.attach(this, event.getDragboard().getFiles().getFirst().toPath());
            event.setDropCompleted(true);
            event.consume();
        });

        relocate(note.layout().x(), note.layout().y());
        setPrefSize(note.layout().width(), note.layout().height());
        setMinSize(MIN_WIDTH, MIN_HEIGHT);
        setMaxSize(MAX_WIDTH, MAX_HEIGHT);
    }

    public String id() {

        return id;
    }

    /** Where the card is, how large, its stacking order and its colour, as the board should keep them. */
    public NoteLayout noteLayout() {

        return new NoteLayout(getLayoutX(), getLayoutY(), getPrefWidth(), getPrefHeight(), order, color);
    }

    public void setOrder(int order) {

        this.order = order;
    }

    public void setColor(NoteColor color) {

        for (NoteColor each : NoteColor.values())
            getStyleClass().remove(colorStyle(each));
        getStyleClass().add(colorStyle(color));
        this.color = color;
        swatches.setCurrent(color);
    }

    public void setSelected(boolean selected) {

        pseudoClassStateChanged(SELECTED, selected);
    }

    public void setDuplicateOnImport(boolean duplicate) {

        duplicateOnImport = duplicate;
        // The band rounds the card's top corners, which the toolbar under it then leaves square.
        getStyleClass().remove(DUPLICATE_STYLE);
        if (duplicate)
            getStyleClass().add(DUPLICATE_STYLE);
        band.setVisible(duplicate);
        band.setManaged(duplicate);
    }

    public boolean isDuplicateOnImport() {

        return duplicateOnImport;
    }

    /** Hands the text to the board now if it is still waiting for the author to pause, so nothing typed is lost. */
    public void flushText() {

        if (saveDelay.getStatus() != PauseTransition.Status.RUNNING)
            return;

        saveDelay.stop();
        board.textChanged(this, text.getText());
    }

    /** The file the note carries, if any, on disk or missing. */
    public Optional<Path> attachment() {

        return fileSlot.file();
    }

    /** Shows {@code file} under the text, or nothing. */
    public void setAttachment(Optional<Path> file) {

        fileSlot.setFile(file);
    }

    /** True if the note's file is shown as a picture, which the board draws for it. */
    public boolean wantsPicture() {

        return fileSlot.wantsPicture();
    }

    public void showPicture(Image picture) {

        fileSlot.showPicture(picture);
    }

    /** Shows how far the copy of {@code name} into the note has come, from 0 to 1. */
    public void showCopying(String name, double done) {

        fileSlot.showCopying(name, done);
    }

    /** The style class that gives a note, or a swatch, {@code color}. */
    static String colorStyle(NoteColor color) {

        return COLOR_STYLE.formatted(color.key());
    }

    /** Puts the caret in the note's text, ready for typing. */
    public void focusText() {

        text.requestFocus();
    }

    // Layout

    /**
     * The toolbar at the top: the grip to drag the note by, then, on hover or while the note is selected, the paperclip
     * that opens the file menu, the note's colour and its delete button.
     */
    private HBox topStrip() {

        Region grip = new Region();
        grip.setShape(Icons.shape("grip"));
        grip.getStyleClass().add("note-grip");

        SVGPath paperclip = Icons.shape("paperclip");
        paperclip.getStyleClass().add("note-paperclip");
        Button file = new Button(null, paperclip);
        file.getStyleClass().add("note-file-button");
        file.setTooltip(new Tooltip("File"));
        file.setOnAction(event -> {
            Bounds bounds = file.localToScreen(file.getBoundsInLocal());
            board.showMenu(this, fileMenuItems(), bounds.getMinX(), bounds.getMaxY());
        });

        Button colorDot = new Button();
        colorDot.getStyleClass().add("note-color-dot");
        colorDot.setTooltip(new Tooltip("Colour"));
        colorDot.setOnAction(event -> swatches.toggle());

        Button delete = new Button("×");
        delete.getStyleClass().add("note-delete");
        delete.setTooltip(new Tooltip("Delete"));
        delete.setOnAction(event -> board.delete(this));

        HBox tools = new HBox(file, colorDot, delete);
        tools.getStyleClass().add("note-tools");
        tools.setAlignment(Pos.CENTER_RIGHT);

        HBox strip = new HBox(grip, hSpacer(), tools);
        strip.getStyleClass().add("note-strip");
        strip.setAlignment(Pos.CENTER_LEFT);
        drag.moveBy(strip);

        return strip;
    }

    /** The note's menu, on a right-click: the file menu, then the colours and Delete. */
    private List<MenuItem> menuItems() {

        List<MenuItem> items = new ArrayList<>(fileMenuItems());
        Menu colors = new Menu("Colour");
        for (NoteColor each : NoteColor.values())
            colors.getItems().add(menuItem(each.label(), () -> recolor(each)));

        items.addAll(List.of(new SeparatorMenuItem(), colors, menuItem("Delete", () -> board.delete(this))));
        return items;
    }

    /** The file menu: with a file, Open, Show in folder, Replace file, Remove file; without, Attach file and Paste. */
    private List<MenuItem> fileMenuItems() {

        List<MenuItem> items = new ArrayList<>();
        if (attachment().isPresent()) {
            items.add(menuItem("Open", () -> board.openFile(this), Files.exists(attachment().get())));
            items.add(menuItem("Show in folder", () -> board.showInFolder(this)));
            items.add(menuItem("Replace file…", () -> board.chooseFile(this)));
            items.add(menuItem("Remove file", () -> board.removeFile(this)));
        } else {
            items.add(menuItem("Attach file…", () -> board.chooseFile(this)));
            items.add(menuItem("Paste", () -> board.paste(this)));
        }
        return items;
    }

    private void recolor(NoteColor newColor) {

        if (newColor == color)
            return;

        setColor(newColor);
        board.layoutChanged(this);
    }

    /** The Duplicate on Import band, above the strip, with its own button to clear it. */
    private HBox duplicateBand() {

        Label tag = new Label("DUPLICATE ON IMPORT");
        tag.setTooltip(new Tooltip("Changed on both computers since the last import or export. Keep the version you"
                + " want, and delete the other."));

        Button clear = new Button("×");
        clear.getStyleClass().add("note-band-clear");
        clear.setOnAction(event -> board.clearDuplicateTag(this));

        HBox row = new HBox(tag, hSpacer(), clear);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** The bottom-right corner, dragged to resize the note. */
    private Region resizeCorner() {

        Region corner = new Region();
        corner.setShape(Icons.shape("resize-corner"));
        corner.getStyleClass().add("note-resize");
        corner.setManaged(false);
        drag.resizeBy(corner);

        // Left out of the card's layout, so it is sized from its style and placed at the corner by hand.
        layoutBoundsProperty().addListener((obs, old, bounds) -> {
            corner.autosize();
            corner.relocate(bounds.getWidth() - corner.getWidth(), bounds.getHeight() - corner.getHeight());
        });
        return corner;
    }
}
