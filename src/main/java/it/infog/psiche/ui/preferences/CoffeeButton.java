package it.infog.psiche.ui.preferences;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.function.Consumer;

/** Buy Me a Coffee's logo on their yellow, as a button that opens Davide's page there, in About and Support. */
final class CoffeeButton {

    private static final String PAGE = "https://buymeacoffee.com/davide.truzzi/e/598055";
    // Buy Me a Coffee's own logo, dark on their yellow as their buttons show it, from their brand bundle.
    private static final String LOGO = "buy-me-a-coffee.png";
    private static final double LOGO_HEIGHT = 28;

    private CoffeeButton() {
    }

    /** @param browser opens a web address in the system's default browser */
    static Button create(Consumer<String> browser) {

        // Read at twice the height shown, so it stays sharp on screens that scale the window up.
        Image image = new Image(CoffeeButton.class.getResource(LOGO).toExternalForm(), 0, LOGO_HEIGHT * 2, true,
                true);
        ImageView logo = new ImageView(image);
        logo.setFitHeight(LOGO_HEIGHT);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);

        Button button = new Button(null, logo);
        button.getStyleClass().add("coffee-button");
        button.setAccessibleText("Buy me a coffee");
        button.setTooltip(new Tooltip(PAGE));
        button.setOnAction(event -> browser.accept(PAGE));
        return button;
    }
}
