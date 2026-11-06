package it.infog.psiche.ui.common;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;

/**
 * The frame of a dialog that lists what was left without a chapter, group by group, as Detached notes and Detached
 * images do: the groups in a list that scrolls, a line under rules between them, a hint below, and Close. Each group
 * is headed by the chapter it belonged to, above why and when it was left, with its buttons beside them, and shows
 * what it holds under them. The dialog stays open: the groups are shown again after each change, and greyed out while
 * it is made.
 */
public final class DetachedDialog {

    private static final ButtonType CLOSE = new ButtonType("Close", ButtonData.CANCEL_CLOSE);

    private final Dialog<ButtonType> dialog = new Dialog<>();
    private final VBox groupList = new VBox();
    private final String none;

    /**
     * @param title the dialog's title, as "Detached notes"
     * @param hint  the line under the list, as what deleting for good means
     * @param none  what the list says with no group left, as "No detached notes."
     */
    public DetachedDialog(Window owner, String title, String hint, String none) {

        this.none = none;

        Dialogs.ownAndStyle(dialog, owner);
        dialog.setHeaderText(title);
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().setAll(CLOSE);

        groupList.getStyleClass().add("detached-groups");
        ScrollPane scroll = new ScrollPane(groupList);
        scroll.setFitToWidth(true);

        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("history-hint");

        VBox content = new VBox(scroll, hintLabel);
        content.getStyleClass().add("detached");
        dialog.getDialogPane().setContent(content);
    }

    /** Waits until the author closes the dialog. */
    public void showAndWait() {

        dialog.showAndWait();
    }

    /** Shows {@code groups}, each made by {@link #group}, in place of those shown, and lets the author act again. */
    public void setGroups(List<Node> groups) {

        groupList.getChildren().clear();
        groupList.setDisable(false);
        if (groups.isEmpty()) {
            groupList.getChildren().add(wrapping(none, "detached-details"));
            return;
        }

        for (Node group : groups) {
            if (!groupList.getChildren().isEmpty())
                groupList.getChildren().add(rule());
            groupList.getChildren().add(group);
        }
    }

    /** Greys the groups out while a change asked for is made; {@link #setGroups} brings them back. */
    public void setBusy() {

        groupList.setDisable(true);
    }

    public void close() {

        dialog.close();
    }

    /** The dialog's window, which the questions it asks belong to. */
    public Window window() {

        return dialog.getDialogPane().getScene().getWindow();
    }

    /**
     * A group: {@code name} above {@code details}, the buttons beside them keeping their whole text while a long name
     * wraps, and {@code items}, small cards, under them.
     */
    public static VBox group(String name, String details, List<Node> items, Button... buttons) {

        VBox source = new VBox(wrapping(name, "detached-chapter"), wrapping(details, "detached-details"));
        source.getStyleClass().add("detached-source");
        source.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(source, Priority.ALWAYS);

        HBox heading = new HBox(source);
        for (Button button : buttons) {
            button.setMinWidth(Region.USE_PREF_SIZE);
            heading.getChildren().add(button);
        }
        heading.getStyleClass().add("detached-heading");
        heading.setAlignment(Pos.CENTER_LEFT);

        FlowPane cards = new FlowPane();
        cards.getStyleClass().add("detached-cards");
        cards.getChildren().setAll(items);

        VBox view = new VBox(heading, cards);
        view.getStyleClass().add("detached-group");
        return view;
    }

    private static Label wrapping(String text, String styleClass) {

        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        return label;
    }

    private static Region rule() {

        Region rule = new Region();
        rule.getStyleClass().add("detached-rule");
        return rule;
    }
}
