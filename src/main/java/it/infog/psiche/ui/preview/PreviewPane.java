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
 * neither renders nor scrolls; it catches up with the latest text and line once it shows again. Web and mail links
 * open in the system browser; the preview itself never leaves the page. Its theme can change while it shows a page,
 * which then renders again in the new one.
 */
public final class PreviewPane extends StackPane {

    // Long enough that the preview does not re-render on every keystroke while typing steadily.
    private static final Duration RENDER_DELAY = Duration.millis(500);
    // Short enough to feel immediate, long enough that holding an arrow key scrolls the page once, not per line.
    private static final Duration FOLLOW_DELAY = Duration.millis(120);

    /*
     * Scrolls to Markdown line %d, using the data-line marks the renderer puts on each block. A line inside a block,
     * or between two, lands proportionally between the block it is in and the next one, which goes a third of the way
     * down the view. Unless the second argument asks for it exactly, the page only moves once that point leaves the
     * middle of the view, so it stays put while typing.
     */
    private static final String FOLLOW_LINE = """
            (function (line, exactly) {
                let before = null, after = null;
                for (const block of document.querySelectorAll('[data-line]')) {
                    if (Number(block.dataset.line) > line) {
                        after = block;
                        break;
                    }
                    before = block;
                }
                const top = block => block.getBoundingClientRect().top + window.scrollY;
                let y = 0;
                if (before !== null) {
                    y = top(before);
                    if (after !== null) {
                        const from = Number(before.dataset.line), to = Number(after.dataset.line);
                        y += (top(after) - y) * (line - from) / (to - from);
                    }
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
    private int followedLine;
    private boolean followExactly;
    private MarkdownRenderer renderer;
    // What the page shows, to render it again in another theme; null while it is blank.
    private String shownMarkdown;
    private Path shownBaseDirectory;

    /**
     * @param renderers render pages in a theme
     * @param theme     the theme to start in
     * @param browser   opens a web or mail link in the system's default application
     */
    public PreviewPane(Function<PreviewTheme, MarkdownRenderer> renderers, PreviewTheme theme,
                       Consumer<String> browser) {

        this.renderers = renderers;
        this.browser = browser;

        view.setPageFill(Color.TRANSPARENT);
        view.setContextMenuEnabled(false);
        // Loading a page resets its scroll position, so scroll the new page to where it should be.
        view.getEngine().getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                view.getEngine().executeScript("window.scrollTo(0, %s)".formatted(scrollAfterLoad));
                showFollowedLine();
                catchLinkClicks();
            }
        });

        renderDelay.setOnFinished(event -> pendingRender.run());
        followDelay.setOnFinished(event -> showFollowedLine());
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

        renderer = renderers.apply(theme);
        pseudoClassStateChanged(DARK, theme == PreviewTheme.DARK);

        // A render already waiting will use the new theme; so will a page loaded later.
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
     * Keeps the part of the page rendered from Markdown {@code line} (zero-based) in view, once the author pauses. A
     * page still loading shows it as soon as it is ready.
     *
     * @param exactly true to put the line a third of the way down even if it is already in view, so the page lines up
     *                with an editor the author is scrolling; false to move the page only when the line leaves the
     *                middle of the view, so it stays put while the author types
     */
    public void followLine(int line, boolean exactly) {

        followedLine = line;
        followExactly = exactly;
        if (showing.get())
            followDelay.playFromStart();
    }

    private void showFollowedLine() {

        if (view.getEngine().getLoadWorker().getState() == Worker.State.SUCCEEDED)
            view.getEngine().executeScript(FOLLOW_LINE.formatted(followedLine, followExactly));
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

    /** Runs the render held back while hidden, which scrolls to the followed line once loaded, or just scrolls. */
    private void catchUp() {

        if (renderWaiting) {
            renderWaiting = false;
            pendingRender.run();
        } else
            showFollowedLine();
    }

    private void load(String markdown, Path baseDirectory, double scroll) {

        scrollAfterLoad = scroll;
        shownMarkdown = markdown;
        shownBaseDirectory = baseDirectory;
        view.getEngine().loadContent(renderer.render(markdown, baseDirectory));
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
