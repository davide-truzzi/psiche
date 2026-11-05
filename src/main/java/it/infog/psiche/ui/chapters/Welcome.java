package it.infog.psiche.ui.chapters;

import it.infog.psiche.ui.common.Logo;

import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.fxmisc.richtext.StyleClassedTextArea;

/**
 * The welcome on the manuscript's empty page while no book is open, with the app's logo behind it. The welcome is the
 * text's placeholder, which shows whenever the text is empty, so it is taken away while a book is open: it would
 * otherwise fill every empty or new chapter as if it were the chapter's text.
 */
final class Welcome {

    private final StyleClassedTextArea area;
    private final StackPane page;
    private final Label text;
    private final StackPane logo = new StackPane();

    /** @param page the page that stacks the text over the logo; the logo goes at the back of it */
    Welcome(StyleClassedTextArea area, StackPane page, String text) {

        this.area = area;
        this.page = page;
        this.text = label(text);

        //the logo, sized to the page and never in the way of the page's own size or the mouse
        logo.getChildren().add(Logo.lockup(logo, 0.6));
        logo.getStyleClass().add("welcome-logo");
        logo.setMinSize(0, 0);
        logo.setPrefSize(0, 0);
        logo.setMouseTransparent(true);
    }

    /** Shows the welcome and its logo on the empty page, as when the app starts, or once the book closes. */
    void show() {

        area.setPlaceholder(text, Pos.TOP_LEFT);
        // The placeholder hides while the text has the focus, so the welcome takes no click nor Tab until it goes.
        area.setMouseTransparent(true);
        area.setFocusTraversable(false);
        // The text kept the focus it had while a chapter was open, which would hide the welcome.
        if (area.isFocused())
            page.requestFocus();
        if (!page.getChildren().contains(logo))
            page.getChildren().addFirst(logo);
    }

    /** Takes the welcome and its logo away, while a book is open. */
    void hide() {

        area.setPlaceholder(null);
        area.setMouseTransparent(false);
        area.setFocusTraversable(true);
        page.getChildren().remove(logo);
    }

    /** The welcome, wrapped to the page's width inside its padding, in the placeholder's colour. */
    private Label label(String text) {

        Label label = new Label(text);
        label.getStyleClass().add("manuscript-welcome");
        label.setWrapText(true);
        label.maxWidthProperty().bind(Bindings.createDoubleBinding(
                () -> area.getWidth() - area.getPadding().getLeft() - area.getPadding().getRight(),
                area.widthProperty(), area.paddingProperty()));
        return label;
    }
}
