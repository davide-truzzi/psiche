package it.infog.psiche.ui.preferences;

import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.Icons;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.function.Consumer;

/**
 * "Keep the lamp lit": the yellow lamp on the toolbar, and the dialog it opens, asking the writers who use Psiche
 * Writer to support it, with the same Buy Me a Coffee button as About. While the pointer is over that button, the
 * dialog's lamp burns as a patron's. The dialog's lamps glow in gradients JavaFX cannot give a shape, so they are
 * pictures, exported from their SVG files.
 */
public final class SupportDialog {

    private static final String LAMP = "lamp";
    private static final String PATRON_LAMP = "lamp-patron";
    private static final double DIALOG_LAMP_HEIGHT = 64;

    private static final String INVITATION = "Keep the lamp lit · Support Psiche Writer";
    private static final String MESSAGE = "Psiche Writer is free and open source, kept alive by the writers who use "
            + "it. If it has helped your book, consider supporting its development.";

    private SupportDialog() {
    }

    /** The lamp, as a toolbar button that runs {@code open}. */
    public static Button lampButton(Runnable open) {

        Region lamp = new Region();
        lamp.setShape(Icons.shape(LAMP));
        lamp.getStyleClass().add("support-lamp");

        Button button = new Button(null, lamp);
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
        // Its wrapped lines all shown: the dialog grows to hold them rather than shrinking them away.
        message.setMinHeight(Region.USE_PREF_SIZE);

        Label support = new Label("Show your support to Davide, you will get a Markdown guide for novelists!");
        support.setWrapText(true);
        support.setMinHeight(Region.USE_PREF_SIZE);

        Button coffee = CoffeeButton.create(browser);
        VBox content = new VBox(glowingLamp(coffee), message, coffee, support);
        content.getStyleClass().add("support-content");
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    /** The dialog's glowing lamp, burning as a patron's, flame high and rays out, while the pointer is over coffee. */
    private static ImageView glowingLamp(Button coffee) {

        Image lamp = Icons.picture(LAMP, DIALOG_LAMP_HEIGHT);
        Image patronLamp = Icons.picture(PATRON_LAMP, DIALOG_LAMP_HEIGHT);

        ImageView picture = new ImageView(lamp);
        picture.setFitHeight(DIALOG_LAMP_HEIGHT);
        picture.setPreserveRatio(true);
        picture.setSmooth(true);
        coffee.hoverProperty().addListener((observable, wasHovered, hovered) ->
                picture.setImage(hovered ? patronLamp : lamp));
        return picture;
    }
}
