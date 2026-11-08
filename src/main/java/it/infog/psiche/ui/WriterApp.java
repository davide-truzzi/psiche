package it.infog.psiche.ui;

import it.infog.psiche.markdown.MarkdownRenderer;
import it.infog.psiche.settings.Keychain;
import it.infog.psiche.settings.LogFile;
import it.infog.psiche.settings.Settings;
import it.infog.psiche.settings.SettingsFolders;
import it.infog.psiche.settings.model.SettingsFolder;
import it.infog.psiche.settings.model.WindowBounds;
import it.infog.psiche.settings.model.enums.PreviewTheme;
import it.infog.psiche.ui.common.AppInfo;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Launches the editor, where the window was last time; an optional first argument is a book folder to open on start,
 * and without one the book opened last opens if the author chose that in Preferences.
 */
public final class WriterApp extends Application {

    // Before the first logger, which is made below: slf4j-simple reads where to write only once.
    static {
        LogFile.useWhenPackaged();
    }

    private static final Logger log = LoggerFactory.getLogger(WriterApp.class);

    // The app's icon in the sizes window managers and taskbars ask for; the small ones are drawn by hand, not scaled.
    private static final List<Integer> ICON_SIZES = List.of(16, 32, 48, 64, 128, 256);

    // Registered with JavaFX at startup so both the controls and the WebView preview can use them by family name.
    // Each style is a static font of its own, as JavaFX only draws a variable font at its default weight.
    private static final List<String> FONTS = List.of(
            "CourierPrime-Regular.ttf", "CourierPrime-Bold.ttf", "CourierPrime-Italic.ttf",
            "LibertinusSerif-Regular.otf", "LibertinusSerif-Italic.otf",
            "LibertinusSerif-Bold.otf", "LibertinusSerif-BoldItalic.otf");

    // Smaller than this, a window remembered from last time is taken for a mistake and not restored.
    private static final double MIN_WIDTH = 640;
    private static final double MIN_HEIGHT = 400;

    private BookWorkspace workspace;
    private Settings settings;
    private Keychain keychain;
    private Stage stage;

    public static void main(String[] args) {

        launch(args);
    }

    @Override
    public void start(Stage stage) {

        //load fonts
        FONTS.forEach(WriterApp::loadFont);

        //settings, in the user's settings folder, or next to the app when it runs portable
        this.stage = stage;
        SettingsFolder settingsFolder = SettingsFolders.resolve();
        log.info("{} {} on {} {}, Java {}; settings in {}", AppInfo.NAME, AppInfo.version().orElse("(build)"),
                System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("java.version"),
                settingsFolder.path());
        settings = new Settings(settingsFolder);
        keychain = new Keychain(settingsFolder);

        //load preview CSS: the dark theme only recolors the default one, so it comes after it
        String previewStyle = readResource("preview.css");
        String darkStyle = readResource("preview-dark.css");
        Function<PreviewTheme, MarkdownRenderer> renderers = theme -> new MarkdownRenderer(switch (theme) {
            case DEFAULT -> previewStyle;
            case DARK -> previewStyle + darkStyle;
        });

        workspace = new BookWorkspace(renderers, readResource("welcome.md"), getHostServices()::showDocument,
                settings, keychain);

        //generate scene with app CSS
        Scene scene = new Scene(workspace, 1360, 860);
        scene.getStylesheets().add(WriterApp.class.getResource("writer.css").toExternalForm());
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), workspace::save);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN,
                KeyCombination.SHIFT_DOWN), workspace::saveAll);

        //complete and show stage
        // TODO: remove the version when production ready, at the first stable release: About keeps it.
        stage.setTitle(AppInfo.version().map(version -> AppInfo.NAME + " " + version).orElse(AppInfo.NAME));
        ICON_SIZES.forEach(size -> stage.getIcons().add(
                new Image(WriterApp.class.getResource("icons/psiche-%d.png".formatted(size)).toExternalForm())));
        stage.setScene(scene);
        // The window stays until the workspace is ready, which may take a Synchronize: it closes it then.
        stage.setOnCloseRequest(event -> {
            event.consume();
            workspace.requestClose(stage::close);
        });
        settings.windowBounds().filter(WriterApp::fitsOnScreen).ifPresent(this::place);
        stage.show();

        //this line opens a book automatically when you pass its folder on the command line
        //E.G. ./gradlew run --args="/path/to/my-book" or java … WriterApp /path/to/my-book
        Optional<Path> book = getParameters().getUnnamed().stream().findFirst().map(Path::of);

        //without one, the book opened last, if the author asked for it and it is still there
        if (book.isEmpty() && settings.preferences().reopenLastBook())
            book = settings.recentBooks().books().stream().findFirst().filter(Files::isDirectory);

        book.ifPresent(workspace::openBook);
    }

    @Override
    public void stop() {

        rememberBounds();
        workspace.close();
        // The master password, typed once this session, goes with it.
        keychain.lock();
    }

    /** Puts the window where {@code bounds} say, maximized if it was. */
    private void place(WindowBounds bounds) {

        stage.setX(bounds.x());
        stage.setY(bounds.y());
        stage.setWidth(bounds.width());
        stage.setHeight(bounds.height());
        stage.setMaximized(bounds.maximized());
    }

    /**
     * Remembers where the window is. A maximized window covers the screen, so its size is not worth keeping: the size
     * it had before, as last remembered, is kept instead, for when it is restored.
     */
    private void rememberBounds() {

        WindowBounds now = new WindowBounds(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(), false);
        WindowBounds restored = stage.isMaximized() ? settings.windowBounds().orElse(now) : now;

        settings.saveWindowBounds(new WindowBounds(restored.x(), restored.y(), restored.width(), restored.height(),
                stage.isMaximized()));
    }

    /**
     * True if a window at {@code bounds} would be usable: not too small, and with the middle of its title bar on one
     * of the screens there are now, so a window last seen on a monitor since unplugged opens where the author sees it.
     */
    private static boolean fitsOnScreen(WindowBounds bounds) {

        if (bounds.width() < MIN_WIDTH || bounds.height() < MIN_HEIGHT)
            return false;

        double titleX = bounds.x() + bounds.width() / 2;
        double titleY = bounds.y() + 16;
        return Screen.getScreens().stream().anyMatch(screen -> screen.getVisualBounds().contains(titleX, titleY));
    }

    private static void loadFont(String file) {

        try (InputStream in = WriterApp.class.getResourceAsStream("fonts/" + file)) {
            if (Font.loadFont(in, 0) == null) {
                log.warn("Font {} could not be loaded; falling back to a system font", file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String readResource(String name) {

        try (InputStream in = WriterApp.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
