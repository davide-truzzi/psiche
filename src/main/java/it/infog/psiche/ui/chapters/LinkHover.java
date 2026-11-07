package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.TextLinks;
import it.infog.psiche.markdown.model.TextLink;

import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Opens a link from the manuscript, as from the preview: with Ctrl held (⌘ on macOS) over a link that leads somewhere,
 * the pointer turns into a hand with "Open link" under it, and a click opens the link. Without Ctrl, a click puts the
 * caret in the link, to edit it, as anywhere else. The tip is drawn on a layer of its own
 * over the text, which the pointer goes through: a tooltip's window would take the pointer moving onto the lines it
 * covers.
 */
final class LinkHover {

    private static final PseudoClass OVER_LINK = PseudoClass.getPseudoClass("over-link");
    // Where the tip shows: right of the pointer, clear of the hand, and just below the link's line.
    private static final double TIP_OFFSET_X = 14;
    private static final double TIP_GAP = 4;
    // The key Shortcut stands for: ⌘ on macOS, Ctrl elsewhere.
    private static final KeyCode SHORTCUT_KEY =
            System.getProperty("os.name", "").startsWith("Mac") ? KeyCode.COMMAND : KeyCode.CONTROL;

    private final StyleClassedTextArea area;
    private final Label tip = new Label("Open link");
    private final Pane layer = new Pane(tip);
    private Predicate<String> opens = destination -> false;
    private Consumer<String> open = destination -> {};
    // The links of the text as it was last looked at, found again only once it changes.
    private String linkedText;
    private List<TextLink> links = List.of();
    // The link under the pointer while Ctrl is held, which a click opens; null otherwise.
    private TextLink hovered;
    // Where the pointer last was over the text, for Ctrl pressed or released without moving it.
    private MouseEvent lastMove;
    // True from a press that opened a link to its release, which the text then does not take as a click of its own.
    private boolean opening;
    private final EventHandler<KeyEvent> keys = event -> {
        if (lastMove != null)
            hover(isShortcutDown(event) ? linkAt(lastMove) : null, lastMove);
    };

    LinkHover(StyleClassedTextArea area) {

        this.area = area;
        tip.getStyleClass().add("tooltip");
        tip.setVisible(false);
        layer.setMouseTransparent(true);
        area.addEventFilter(MouseEvent.MOUSE_MOVED, this::moved);
        area.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
            lastMove = null;
            hover(null, event);
        });
        area.addEventFilter(MouseEvent.MOUSE_PRESSED, this::pressed);
        area.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::swallowWhileOpening);
        area.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            swallowWhileOpening(event);
            opening = false;
        });
        // Ctrl pressed or released with the pointer still, watched in the whole window, as the text may lack the focus.
        area.sceneProperty().addListener((obs, old, scene) -> {
            if (old != null)
                old.removeEventFilter(KeyEvent.ANY, keys);
            if (scene != null)
                scene.addEventFilter(KeyEvent.ANY, keys);
        });
    }

    /** The layer the tip is drawn on, to stack over the text. */
    Node layer() {

        return layer;
    }

    /**
     * Opens with {@code open} the destination of a link the author Ctrl-clicks, if {@code opens} says it leads
     * somewhere.
     */
    void setOpening(Predicate<String> opens, Consumer<String> open) {

        this.opens = opens;
        this.open = open;
    }

    private void moved(MouseEvent event) {

        lastMove = event;
        hover(event.isShortcutDown() ? linkAt(event) : null, event);
    }

    private void pressed(MouseEvent event) {

        if (hovered == null || !event.isShortcutDown() || !event.isPrimaryButtonDown())
            return;

        event.consume();
        opening = true;
        String destination = hovered.destination();
        hover(null, event);
        open.accept(destination);
    }

    private void swallowWhileOpening(MouseEvent event) {

        if (opening)
            event.consume();
    }

    /** Shows the hand and the tip over {@code link}, or the text's own pointer with none. */
    private void hover(TextLink link, MouseEvent event) {

        if (link == hovered)
            return;

        hovered = link;
        area.pseudoClassStateChanged(OVER_LINK, link != null);
        tip.setVisible(link != null);
        if (link == null)
            return;

        double lineBottom = area.getCharacterBoundsOnScreen(link.range().start(), link.range().end())
                .map(Bounds::getMaxY).orElse(event.getScreenY());
        Point2D at = layer.screenToLocal(event.getScreenX() + TIP_OFFSET_X, lineBottom + TIP_GAP);
        tip.autosize();
        tip.relocate(at.getX(), at.getY());
    }

    /** The link under the pointer, if it leads somewhere; null if there is none. */
    private TextLink linkAt(MouseEvent event) {

        String text = area.getText();
        if (!text.equals(linkedText)) {
            linkedText = text;
            links = TextLinks.of(text);
        }

        int index = area.hit(event.getX(), event.getY()).getCharacterIndex().orElse(-1);
        Optional<TextLink> link = links.stream()
                .filter(found -> found.range().start() <= index && index < found.range().end())
                .findFirst();
        return link.filter(found -> found.equals(hovered) || opens.test(found.destination())).orElse(null);
    }



    /**
     * True if Shortcut is held as {@code event} happens. The Shortcut key's own press and release say so by their
     * kind: on some systems they carry the keys held before them.
     */
    private static boolean isShortcutDown(KeyEvent event) {

        if (event.getCode() != SHORTCUT_KEY)
            return event.isShortcutDown();

        return event.getEventType() == KeyEvent.KEY_PRESSED;
    }
}
