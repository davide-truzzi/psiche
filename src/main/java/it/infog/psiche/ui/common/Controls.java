package it.infog.psiche.ui.common;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

import java.util.List;
import java.util.function.Function;

/** Factories for the small controls the workspace builds in several places. */
public final class Controls {

    private Controls() {
    }

    /** Shows {@code node}, or hides it and gives up its room in the layout. */
    public static void setShown(Node node, boolean shown) {

        node.setVisible(shown);
        node.setManaged(shown);
    }

    /** A generic button */
    public static Button button(String text, Runnable action) {

        Button button = new Button(text);
        button.setOnAction(event -> action.run());

        return button;
    }

    /** A generic menu item */
    public static MenuItem menuItem(String text, Runnable action) {

        MenuItem item = new MenuItem(text);
        item.setOnAction(event -> action.run());

        return item;
    }

    /** A menu item, greyed out unless {@code enabled}. */
    public static MenuItem menuItem(String text, Runnable action, boolean enabled) {

        MenuItem item = menuItem(text, action);
        item.setDisable(!enabled);

        return item;
    }

    /**
     * The usual editing of {@code text}: Undo, Redo, Cut, Copy, Paste, Delete and Select All, each enabled if it
     * applies to the text and selection as they are now.
     */
    public static List<MenuItem> editingItems(TextInputControl text) {

        boolean selection = text.getSelection().getLength() > 0;

        return List.of(
                menuItem("Undo", text::undo, text.isUndoable()),
                menuItem("Redo", text::redo, text.isRedoable()),
                new SeparatorMenuItem(),
                menuItem("Cut", text::cut, selection),
                menuItem("Copy", text::copy, selection),
                menuItem("Paste", text::paste, Clipboard.getSystemClipboard().hasString()),
                menuItem("Delete", () -> text.replaceSelection(""), selection),
                new SeparatorMenuItem(),
                menuItem("Select All", text::selectAll, !text.getText().isEmpty()));
    }

    /** An empty region that takes the free width of an {@link HBox}, pushing the nodes after it to the right. */
    public static Region hSpacer() {

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return spacer;
    }

    /**
     * {@code rows}, a form of labels and fields, framed like an HTML fieldset: a border with {@code legend} set into
     * its top edge. The labels' column is at least {@code labelWidth} wide, so the fields of several fieldsets line
     * up, and the fields' column takes the rest.
     */
    public static StackPane fieldset(String legend, GridPane rows, double labelWidth) {

        return fieldset(legend, formRows(rows, labelWidth));
    }

    /**
     * {@code content} framed like an HTML fieldset, as {@link #fieldset(String, GridPane, double)} frames rows: for
     * rows that scroll, a scroll pane around them.
     */
    public static StackPane fieldset(String legend, Region content) {

        content.getStyleClass().add("fieldset-rows");
        Label title = new Label(legend);
        title.getStyleClass().add("fieldset-legend");
        StackPane.setAlignment(title, Pos.TOP_LEFT);

        StackPane fieldset = new StackPane(content, title);
        fieldset.getStyleClass().add("fieldset");
        return fieldset;
    }

    /**
     * {@code rows} as a form of labels and fields: the labels' column at least {@code labelWidth} wide, so the fields
     * of several forms line up, and the fields' column taking the rest.
     */
    public static GridPane formRows(GridPane rows, double labelWidth) {

        rows.getStyleClass().add("book-form");
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(labelWidth);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        rows.getColumnConstraints().addAll(labels, fields);
        return rows;
    }

    /** A line of help under a field, in the form's quieter text. */
    public static Label hint(String text) {

        Label hint = new Label(text);
        hint.getStyleClass().add("form-hint");
        return hint;
    }

    /** A box to pick one of {@code items} by {@code label}, as wide as the form. */
    public static <T> ComboBox<T> choices(List<T> items, Function<T, String> label) {

        ComboBox<T> combo = new ComboBox<>();
        combo.getItems().setAll(items);
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(T item) {
                return item == null ? "" : label.apply(item);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        });
        return combo;
    }
}
