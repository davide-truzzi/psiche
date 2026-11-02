package it.infog.psiche.ui.preview;

import it.infog.psiche.markdown.MarkdownRenderer;
import it.infog.psiche.settings.model.enums.PreviewTheme;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Worker;
import javafx.css.PseudoClass;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The open chapter typeset as a page of the book, following the editor as the author types. While it is hidden, it
 * neither renders nor scrolls; it catches up with the latest text and position once it shows again. Web and mail links
 * open in the system browser; the preview itself never leaves the page. Its theme and the size of its text can change
 * while it shows a page, which then renders again in the new ones.
 */
public final class PreviewPane extends StackPane {

    // Long enough that the preview does not re-render on every keystroke while typing steadily.
    private static final Duration RENDER_DELAY = Duration.millis(500);
    // Short enough to feel immediate, long enough that holding an arrow key scrolls the page once, not per line.
    private static final Duration FOLLOW_DELAY = Duration.millis(120);

    /*
     * Scrolls to character %d of the Markdown, using the data-start and data-end marks the renderer puts on each
     * block. The character lands as far down the last block starting at or before it as it is through that block's
     * text, so a long paragraph is followed line by line; one between blocks lands at the bottom of the block above.
     * That point goes a third of the way down the view. Unless the second argument asks for it exactly, the page only
     * moves once that point leaves the middle of the view, so it stays put while typing.
     */
    private static final String FOLLOW_POSITION = """
            (function (position, exactly) {
                let block = null;
                for (const candidate of document.querySelectorAll('[data-start]')) {
                    if (Number(candidate.dataset.start) > position)
                        break;
                    block = candidate;
                }
                let y = 0;
                if (block !== null) {
                    const box = block.getBoundingClientRect();
                    const start = Number(block.dataset.start), end = Number(block.dataset.end);
                    const through = Math.min(1, (position - start) / Math.max(1, end - start));
                    y = box.top + window.scrollY + box.height * through;
                }
                const height = window.innerHeight;
                if (exactly || y < window.scrollY + height * 0.2 || y > window.scrollY + height * 0.8)
                    window.scrollTo(0, y - height / 3);
            })(%d, %b)
            """;

    // Links with these schemes open in the system browser or mail client.
    private static final List<String> EXTERNAL_SCHEMES = List.of("http:", "https:", "mailto:");
    // Lets the frame around the page, seen while it loads, follow the theme.
    private static final PseudoClass DARK = PseudoClass.getPseudoClass("dark");

    private final Function<PreviewTheme, MarkdownRenderer> renderers;
    private final Consumer<String> browser;
    // Held here because the page alone may not keep a listener added from Java alive.
    private final EventListener linkClicks = this::openLink;
    private final WebView view = new WebView();
    private final PauseTransition renderDelay = new PauseTransition(RENDER_DELAY);
    private final PauseTransition followDelay = new PauseTransition(FOLLOW_DELAY);
    private final BooleanProperty showing = new SimpleBooleanProperty(true);
    private Runnable pendingRender = () -> {};
    private double scrollAfterLoad;
    // True while a render is held back because the preview is hidden.
    private boolean renderWaiting;
    private int followedPosition;
    private boolean followExactly;
    private MarkdownRenderer renderer;
    private PreviewTheme theme;
    private int fontSize;
    // What the page shows, to render it again in another theme; null while it is blank.
    private String shownMarkdown;
    private Path shownBaseDirectory;

    /**
     * @param renderers render pages in a theme
     * @param theme     the theme to start in
     * @param fontSize  the size of the page's text to start with, in pixels
     * @param browser   opens a web or mail link in the system's default application
     */
    public PreviewPane(Function<PreviewTheme, MarkdownRenderer> renderers, PreviewTheme theme, int fontSize,
                       Consumer<String> browser) {

        this.renderers = renderers;
        this.fontSize = fontSize;
        this.browser = browser;

        view.setPageFill(Color.TRANSPARENT);
        view.setContextMenuEnabled(false);
        // Loading a page resets its scroll position, so scroll the new page to where it should be.
        view.getEngine().getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                view.getEngine().executeScript("window.scrollTo(0, %s)".formatted(scrollAfterLoad));
                showFollowedPosition();
                catchLinkClicks();
            }
        });

        renderDelay.setOnFinished(event -> pendingRender.run());
        followDelay.setOnFinished(event -> showFollowedPosition());
        showing.addListener((obs, wasShowing, isShowing) -> {
            if (isShowing)
                catchUp();
            else
                holdBack();
        });
        getChildren().add(view);
        getStyleClass().add("preview-frame");
        setTheme(theme);
    }

    /** Shows the page in {@code theme} from now on, rendering the page shown again, where it is scrolled to. */
    public void setTheme(PreviewTheme theme) {

        if (theme == this.theme)
            return;

        this.theme = theme;
        renderer = renderers.apply(theme);
        pseudoClassStateChanged(DARK, theme == PreviewTheme.DARK);
        renderAgain();
    }

    /**
     * Shows the page's text {@code fontSize} pixels high from now on, rendering the page shown again, lined up with
     * the editor, as the text moves with its new size.
     */
    public void setFontSize(int fontSize) {

        if (fontSize == this.fontSize)
            return;

        this.fontSize = fontSize;
        followExactly = true;
        renderAgain();
    }

    /** Renders the page shown again, where it is scrolled to, once something about how it looks has changed. */
    private void renderAgain() {

        // A render already waiting will look the new way; so will a page loaded later.
        if (shownMarkdown == null || renderWaiting)
            return;

        String markdown = shownMarkdown;
        Path baseDirectory = shownBaseDirectory;
        if (showing.get())
            load(markdown, baseDirectory, currentScroll());
        else
            hold(() -> load(markdown, baseDirectory, currentScroll()));
    }

    /** False while the preview is hidden, as behind another tab, so it does no work nobody would see. */
    public BooleanProperty showingProperty() {
        return showing;
    }

    /**
     * Renders at once, scrolled to the top, and drops any render still waiting for the author to pause. A hidden
     * preview renders when it shows again.
     */
    public void render(String markdown, Path baseDirectory) {

        renderDelay.stop();
        if (showing.get())
            load(markdown, baseDirectory, 0);
        else
            hold(() -> load(markdown, baseDirectory, 0));
    }

    /**
     * Renders once the author pauses typing, keeping the current scroll position. Each call restarts the wait and
     * replaces the previous pending render.
     *
     * @param baseDirectory directory that relative links and images in {@code markdown} resolve against
     */
    public void renderLater(String markdown, Path baseDirectory) {

        schedule(() -> load(markdown, baseDirectory, currentScroll()));
    }

    /** Blanks the preview after the same pause as {@link #renderLater}, replacing any pending render. */
    public void clearLater() {

        schedule(() -> {
            shownMarkdown = null;
            view.getEngine().loadContent("");
        });
    }

    /**
     * Keeps the part of the page rendered from the Markdown's character {@code position} (zero-based) in view, once
     * the author pauses. A page still loading shows it as soon as it is ready.
     *
     * @param exactly true to put the position a third of the way down even if it is already in view, so the page lines
     *                up with an editor the author is scrolling; false to move the page only when the position leaves
     *                the middle of the view, so it stays put while the author types
     */
    public void followPosition(int position, boolean exactly) {

        followedPosition = position;
        followExactly = exactly;
        if (showing.get())
            followDelay.playFromStart();
    }

    private void showFollowedPosition() {

        if (view.getEngine().getLoadWorker().getState() == Worker.State.SUCCEEDED)
            view.getEngine().executeScript(FOLLOW_POSITION.formatted(followedPosition, followExactly));
    }

    private void schedule(Runnable render) {

        if (showing.get()) {
            pendingRender = render;
            renderDelay.playFromStart();
        } else
            hold(render);
    }

    private void hold(Runnable render) {

        pendingRender = render;
        renderWaiting = true;
    }

    /** Stops the timers on hiding; a render they were waiting to run is kept for when the preview shows again. */
    private void holdBack() {

        if (renderDelay.getStatus() == Animation.Status.RUNNING)
            renderWaiting = true;
        renderDelay.stop();
        followDelay.stop();
    }

    /** Runs the render held back while hidden, which scrolls to the followed position once loaded, or just scrolls. */
    private void catchUp() {

        if (renderWaiting) {
            renderWaiting = false;
            pendingRender.run();
        } else
            showFollowedPosition();
    }

    private void load(String markdown, Path baseDirectory, double scroll) {

        scrollAfterLoad = scroll;
        shownMarkdown = markdown;
        shownBaseDirectory = baseDirectory;
        view.getEngine().loadContent(renderer.render(markdown, baseDirectory, fontSize));
    }

    /** Listens for clicks on the whole page, so one listener covers every link in it. */
    private void catchLinkClicks() {

        Document page = view.getEngine().getDocument();
        if (page != null)
            ((EventTarget) page).addEventListener("click", linkClicks, false);
    }

    /**
     * Hands a web or mail link to the system browser. Any other link, such as one to a chapter file, would take the
     * preview away from the chapter, so it does nothing; links to a place on the same page still work.
     */
    private void openLink(Event event) {

        Element link = enclosingLink((Node) event.getTarget());
        if (link == null)
            return;

        String href = link.getAttribute("href").strip();
        if (href.startsWith("#"))
            return;

        event.preventDefault();
        String lowerCase = href.toLowerCase(Locale.ROOT);
        if (EXTERNAL_SCHEMES.stream().anyMatch(lowerCase::startsWith))
            browser.accept(href);
    }

    /** The link {@code node} is in, or null if it is not inside one. */
    private static Element enclosingLink(Node node) {

        for (; node != null; node = node.getParentNode())
            if (node instanceof Element element && element.getTagName().equalsIgnoreCase("a")
                    && element.hasAttribute("href"))
                return element;
        return null;
    }

    private double currentScroll() {

        return ((Number) view.getEngine().executeScript("window.scrollY")).doubleValue();
    }
}
