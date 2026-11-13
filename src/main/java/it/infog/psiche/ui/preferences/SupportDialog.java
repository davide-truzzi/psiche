package it.infog.psiche.ui.preferences;

import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.Icons;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.function.Consumer;

/**
 * "Keep the lamp lit": the yellow lamp on the toolbar, and the dialog it opens, asking the writers who use Psiche
 * Writer to support it, with the same Buy Me a Coffee button as About.
 */
public final class SupportDialog {

    private static final String INVITATION = "Keep the lamp lit · Support Psiche Writer";
    private static final String MESSAGE = "Psiche Writer is free and open source, kept alive by the writers who use "
            + "it. If it has helped your book, consider supporting its development.";

    private SupportDialog() {
    }

    /** The lamp, as a toolbar button that runs {@code open}. */
    public static Button lampButton(Runnable open) {

        Button button = new Button(null, lamp("support-lamp"));
        button.getStyleClass().add("support-button");
        button.setAccessibleText(INVITATION);
        button.setTooltip(new Tooltip(INVITATION));
        button.setOnAction(event -> open.run());
        return button;
    }

    /** @param browser opens a web address in the system's default browser */
    public static void show(Window owner, Consumer<String> browser) {

        Dialog<ButtonType> dialog = new Dialog<>();
        Dialogs.ownAndStyle(dialog, owner);
        dialog.setHeaderText("Keep the lamp lit");
        dialog.getDialogPane().getStyleClass().add("support");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);

        Label message = new Label(MESSAGE);
        message.setWrapText(true);

        VBox content = new VBox(lamp("support-dialog-lamp"), message, CoffeeButton.create(browser));
        content.getStyleClass().add("support-content");
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    /** The lamp's drawing, as the shape of a region the style sheet colours and sizes. */
    private static Region lamp(String styleClass) {

        Region lamp = new Region();
        lamp.setShape(Icons.shape("lamp"));
        lamp.getStyleClass().add(styleClass);
        return lamp;
    }
}
