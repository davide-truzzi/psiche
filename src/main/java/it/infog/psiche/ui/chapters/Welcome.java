package it.infog.psiche.ui.chapters;

import it.infog.psiche.ui.common.Logo;

import javafx.css.PseudoClass;
import javafx.scene.layout.StackPane;
import org.fxmisc.richtext.StyleClassedTextArea;

/**
 * The welcome on the manuscript's page while no book is open, with the app's logo behind it. The welcome is the
 * manuscript's own text, read-only and in grey, so its links are teal and open on Ctrl+click as a chapter's do; a
 * chapter opened takes its place, and an empty one shows empty.
 */
final class Welcome {

    private static final PseudoClass WELCOMING = PseudoClass.getPseudoClass("welcoming");

    private final StyleClassedTextArea area;
    private final StackPane page;
    private final String text;
    private final StackPane logo = new StackPane();

    /** @param page the page that stacks the text over the logo; the logo goes at the back of it */
    Welcome(StyleClassedTextArea area, StackPane page, String text) {

        this.area = area;
        this.page = page;
        this.text = text;

        //the logo, sized to the page and never in the way of the page's own size or the mouse
        logo.getChildren().add(Logo.lockup(logo, 0.6));
        logo.getStyleClass().add("welcome-logo");
        logo.setMinSize(0, 0);
        logo.setPrefSize(0, 0);
        logo.setMouseTransparent(true);
    }

    /** The welcome, the Markdown the manuscript shows while no book is open. */
    String text() {

        return text;
    }

    /** Shows the logo behind the text, and the text in the welcome's grey and read-only, as the app starts. */
    void show() {

        area.setEditable(false);
        area.pseudoClassStateChanged(WELCOMING, true);
        if (!page.getChildren().contains(logo))
            page.getChildren().addFirst(logo);
    }

    /** Takes the logo and the welcome's grey away, while a book is open. */
    void hide() {

        area.pseudoClassStateChanged(WELCOMING, false);
        page.getChildren().remove(logo);
    }
}
