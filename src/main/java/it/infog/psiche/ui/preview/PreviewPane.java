package it.infog.psiche.ui.preview;

import it.infog.psiche.common.SafeFiles;
import it.infog.psiche.common.Template;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The open chapter typeset as a page of the book, following the editor as the author types. While it is hidden, it
 * neither renders nor scrolls; it catches up with the latest text and position once it shows again. Web and mail links
 * open in the system browser; the preview itself never leaves the page. Its theme and the size of its text can change
 * while it shows a page, which then renders again in the new ones.
 */
public final class PreviewPane extends StackPane {

    /**
     * How far down the view the preview puts the place it follows, as a share of the view's height: a third. The
     * editor reads the place there while the author scrolls, and brings a comment there, so the two line up.
     */
    public static final double FOLLOW_POINT = 1.0 / 3;

    private static final Logger log = LoggerFactory.getLogger(PreviewPane.class);
    // The page shown, written in the web engine's folder, so the chapter's images, files too, show in it.
    private static final String PAGE_FILE = "preview.html";
    // Long enough that the preview does not re-render on every keystroke while typing steadily.
    private static final Duration RENDER_DELAY = Duration.millis(500);
    // Short enough to feel immediate, long enough that holding an arrow key scrolls the page once, not per line.
    private static final Duration FOLLOW_DELAY = Duration.millis(120);

    // Scrolls the page to a character of the Markdown, as the script explains.
    private static final Template FOLLOW_POSITION = Template.load(PreviewPane.class, "follow-position.js");

    // Puts a new article in place of the page's, the rest of the page kept as it is.
    private static final String REPLACE_ARTICLE = "document.querySelector('article').innerHTML = %s;";
    private static final String SCROLL_TO = "window.scrollTo(0, %s)";

    // Links with these schemes open in the system browser or mail client.
    private static final List<String> EXTERNAL_SCHEMES = List.of("http:", "https:", "mailto:");
    // Lets the frame around the page, seen while it loads, follow the theme.
    private static final PseudoClass DARK = PseudoClass.getPseudoClass("dark");

    private final Function<PreviewTheme, MarkdownRenderer> renderers;
    private final Consumer<String> browser;
    // Held here because the page alone may not keep a listener added from Java alive.
    private final EventListener linkClicks = this::openLink;
    private final WebView view = new WebView();
    private final Path page;
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
    // How the page in the view was loaded; while a render would load it the same way, it only replaces the article.
    private MarkdownRenderer loadedRenderer;
    private Path loadedBaseDirectory;
    // How many pages were loaded from the page's file, which tells each load's address apart from the last one's.
    private int pageLoads;
    private int loadedFontSize;

    /**
     * @param renderers render pages in a theme
     * @param theme     the theme to start in
     * @param fontSize  the size of the page's text to start with, in pixels
     * @param data      the folder the web engine keeps its own data in, which it makes as it needs it
     * @param browser   opens a web or mail link in the system's default application
     */
    public PreviewPane(Function<PreviewTheme, MarkdownRenderer> renderers, PreviewTheme theme, int fontSize,
                       Path data, Consumer<String> browser) {

        this.renderers = renderers;
        this.fontSize = fontSize;
        this.browser = browser;

        // Without one, the engine makes a folder named after the app's main class in the home folder.
        view.getEngine().setUserDataDirectory(data.toFile());
        page = data.resolve(PAGE_FILE);
        view.setPageFill(Color.TRANSPARENT);
        view.setContextMenuEnabled(false);
        // Loading a page resets its scroll position, so scroll the new page to where it should be.
        view.getEngine().getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                view.getEngine().executeScript(SCROLL_TO.formatted(scrollAfterLoad));
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
            loadedRenderer = null;
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
            view.getEngine().executeScript(FOLLOW_POSITION.fill(Map.of(
                    "position", String.valueOf(followedPosition),
                    "exactly", String.valueOf(followExactly),
                    "point", String.valueOf(FOLLOW_POINT))));
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

    /**
     * Shows {@code markdown} scrolled to {@code scroll}. A page loaded the same way, for the same folder, theme and
     * font size, only has its article replaced: loading it whole again would also read its stylesheet and fonts anew,
     * which costs about half as much again, at each pause in typing.
     */
    private void load(String markdown, Path baseDirectory, double scroll) {

        shownMarkdown = markdown;
        shownBaseDirectory = baseDirectory;
        if (isLoadedFor(baseDirectory)) {
            String article = renderer.renderArticle(markdown, baseDirectory);
            view.getEngine().executeScript(REPLACE_ARTICLE.formatted(JavaScript.string(article)));
            view.getEngine().executeScript(SCROLL_TO.formatted(scroll));
            showFollowedPosition();
            return;
        }

        scrollAfterLoad = scroll;
        loadedRenderer = renderer;
        loadedBaseDirectory = baseDirectory;
        loadedFontSize = fontSize;
        loadPage(renderer.render(markdown, baseDirectory, fontSize));
    }

    /**
     * Shows {@code html} from a file of its own: a page given to the engine as text has no address of its own, and
     * WebKit then shows none of the chapter's images, which are files. Each load gets an address of its own, so the
     * engine never shows the last one from its cache. Should the file not be written, the page is shown as text.
     */
    private void loadPage(String html) {

        try {
            Files.createDirectories(page.getParent());
            SafeFiles.writeString(page, html);
        } catch (IOException e) {
            log.warn("Cannot write the preview's page to {}; its images will not show", page, e);
            view.getEngine().loadContent(html);
            return;
        }

        view.getEngine().load(page.toUri() + "?" + ++pageLoads);
    }

    /** True if the page in the view is loaded, and was loaded for {@code baseDirectory}, the theme and font size. */
    private boolean isLoadedFor(Path baseDirectory) {

        return view.getEngine().getLoadWorker().getState() == Worker.State.SUCCEEDED && loadedRenderer == renderer
                && loadedFontSize == fontSize && baseDirectory.equals(loadedBaseDirectory);
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
