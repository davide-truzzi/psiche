package it.infog.psiche.settings;

import it.infog.psiche.common.PropertiesFiles;
import it.infog.psiche.common.SafeFiles;
import it.infog.psiche.settings.model.Preferences;
import it.infog.psiche.settings.model.RecentBooks;
import it.infog.psiche.settings.model.SettingsFolder;
import it.infog.psiche.settings.model.WindowBounds;
import it.infog.psiche.settings.model.enums.ChapterTab;
import it.infog.psiche.settings.model.enums.EditorMode;
import it.infog.psiche.settings.model.enums.PreviewTheme;
import it.infog.psiche.settings.model.enums.WindowTheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * The app's settings, kept in {@value #FILE} in the {@link SettingsFolder}, as a properties file anyone can read: the
 * books opened lately, which the Book menu offers again, what the author chose in Preferences, and where the window
 * was. Beside it, {@value #OWN_WORDS_FILE} lists the words the author added to the spell checker or told it to
 * ignore, one per line, for every book.
 * <p>
 * Settings never stand in the author's way: a missing or damaged file reads as no settings, and a file that cannot
 * be written is logged and left as it was. Each change reads the file again first, so two windows of the app do not
 * undo each other's changes, and is written whole to a temporary file that then takes the file's place, so a crash
 * cannot leave half a file behind. A file that cannot be read is not changed either: written back, it would hold the
 * change alone, and every other setting would be lost.
 */
public final class Settings {

    static final String FILE = "settings.properties";

    private static final Logger log = LoggerFactory.getLogger(Settings.class);
    private static final String COMMENT = "Psiche Writer's settings, written by the app";
    private static final String RECENT = "recent.";
    private static final String REOPEN_LAST_BOOK = "startup.reopen-last-book";
    private static final String NEW_BOOK_LANGUAGE = "book.language";
    private static final String NEW_BOOK_AUTHOR = "book.author";
    private static final String PREVIEW_THEME = "preview.theme";
    private static final String EDITOR_FONT_SIZE = "editor.font-size";
    private static final String PREVIEW_FONT_SIZE = "preview.font-size";
    private static final String CHECK_SPELLING = "spelling.enabled";
    private static final String STRICT_SPELLING = "spelling.strict";
    private static final String HIGHLIGHT_REPETITIONS = "editor.highlight-repetitions";
    private static final String EDITOR_MODE = "editor.mode";
    private static final String LINE_NUMBERS = "editor.line-numbers";
    private static final String WINDOW_THEME = "appearance.theme";
    private static final String WINDOW = "window.";
    private static final String BOOK_COUNTS_SHOWN = "sidebar.book-counts";
    private static final String OPENING_TAB = "tabs.opening";
    private static final String MEDIA_PREVIEW_HEIGHT = "media.preview-height";
    // The Media tab's preview until the author drags it taller or shorter, in pixels.
    private static final double DEFAULT_MEDIA_PREVIEW_HEIGHT = 320;
    private static final String SIDEBAR_WIDTH = "sidebar.width";
    // The chapter list's width until the author drags it.
    private static final double DEFAULT_SIDEBAR_WIDTH = 230;
    private static final String WEB_VIEW_FOLDER = "webview";
    private static final String OWN_WORDS_FILE = "words.txt";

    private final SettingsFolder folder;

    public Settings(SettingsFolder folder) {

        this.folder = folder;
    }

    /**
     * The folder the preview's web engine keeps its data in. Psiche stores nothing there, but the engine always makes
     * one, by default as a folder of its own in the home folder; here it stays with the settings.
     */
    public Path webViewFolder() {

        return folder.path().resolve(WEB_VIEW_FOLDER);
    }

    /** The words the author added to the spell checker or told it to ignore; none if there is no file yet. */
    public Set<String> ownWords() {

        try {
            return Files.readAllLines(folder.path().resolve(OWN_WORDS_FILE), StandardCharsets.UTF_8).stream()
                    .map(String::strip)
                    .filter(word -> !word.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        } catch (NoSuchFileException e) {
            return Set.of();
        } catch (IOException e) {
            log.warn("Cannot read the author's words in {}; using none", folder.path(), e);
            return Set.of();
        }
    }

    /** Adds {@code word} to the author's words, which another window may have changed meanwhile, in order. */
    public void addOwnWord(String word) {

        Set<String> words = new TreeSet<>(ownWords());
        if (words.add(word.strip()))
            writeFile(OWN_WORDS_FILE, String.join("\n", words) + "\n");
    }

    /** The books opened lately, the latest first. */
    public RecentBooks recentBooks() {

        return recentBooks(read());
    }

    /** Remembers that {@code book} was opened, first in the list. */
    public void bookOpened(Path book) {

        updateRecentBooks(recent -> recent.opened(book));
    }

    /** Forgets every book opened lately. */
    public void clearRecentBooks() {

        updateRecentBooks(recent -> RecentBooks.none());
    }

    /** Forgets {@code book} alone, as one that was moved or deleted; the other books keep their places. */
    public void forgetRecentBook(Path book) {

        updateRecentBooks(recent -> recent.without(book));
    }

    private void updateRecentBooks(UnaryOperator<RecentBooks> change) {

        update(settings -> {
            RecentBooks changed = change.apply(recentBooks(settings));

            settings.stringPropertyNames().stream().filter(key -> key.startsWith(RECENT)).forEach(settings::remove);
            for (int i = 0; i < changed.books().size(); i++)
                settings.setProperty(RECENT + (i + 1), stored(changed.books().get(i)));
        });
    }

    /** What the author chose in Preferences; a value that is missing or cannot be read is the default. */
    public Preferences preferences() {

        Properties settings = read();
        Preferences defaults = Preferences.defaults();

        return new Preferences(flag(settings.getProperty(REOPEN_LAST_BOOK), defaults.reopenLastBook()),
                language(settings.getProperty(NEW_BOOK_LANGUAGE)).orElse(defaults.newBookLanguage()),
                settings.getProperty(NEW_BOOK_AUTHOR, defaults.newBookAuthor()).strip(),
                named(PreviewTheme.class, settings.getProperty(PREVIEW_THEME)).orElse(defaults.previewTheme()),
                fontSize(settings.getProperty(EDITOR_FONT_SIZE)).orElse(defaults.editorFontSize()),
                fontSize(settings.getProperty(PREVIEW_FONT_SIZE)).orElse(defaults.previewFontSize()),
                flag(settings.getProperty(CHECK_SPELLING), defaults.checkSpelling()),
                flag(settings.getProperty(STRICT_SPELLING), defaults.strictSpelling()),
                flag(settings.getProperty(HIGHLIGHT_REPETITIONS), defaults.highlightRepetitions()),
                named(EditorMode.class, settings.getProperty(EDITOR_MODE)).orElse(defaults.editorMode()),
                flag(settings.getProperty(LINE_NUMBERS), defaults.alwaysShowLineNumbers()),
                named(WindowTheme.class, settings.getProperty(WINDOW_THEME)).orElse(defaults.windowTheme()));
    }

    /**
     * Remembers what the author chose in Preferences.
     *
     * @return false if they cannot be saved, which is logged: they then last until the app closes
     */
    public boolean savePreferences(Preferences preferences) {

        return update(settings -> {
            settings.setProperty(REOPEN_LAST_BOOK, String.valueOf(preferences.reopenLastBook()));
            settings.setProperty(NEW_BOOK_LANGUAGE, preferences.newBookLanguage());
            settings.setProperty(NEW_BOOK_AUTHOR, preferences.newBookAuthor().strip());
            settings.setProperty(PREVIEW_THEME, written(preferences.previewTheme()));
            settings.setProperty(EDITOR_FONT_SIZE, String.valueOf(preferences.editorFontSize()));
            settings.setProperty(PREVIEW_FONT_SIZE, String.valueOf(preferences.previewFontSize()));
            settings.setProperty(CHECK_SPELLING, String.valueOf(preferences.checkSpelling()));
            settings.setProperty(STRICT_SPELLING, String.valueOf(preferences.strictSpelling()));
            settings.setProperty(HIGHLIGHT_REPETITIONS, String.valueOf(preferences.highlightRepetitions()));
            settings.setProperty(EDITOR_MODE, written(preferences.editorMode()));
            settings.setProperty(LINE_NUMBERS, String.valueOf(preferences.alwaysShowLineNumbers()));
            settings.setProperty(WINDOW_THEME, written(preferences.windowTheme()));
        });
    }

    /** Where the window was when the app last closed; empty the first time, or if a value cannot be read. */
    public Optional<WindowBounds> windowBounds() {

        Properties settings = read();
        try {
            WindowBounds bounds = new WindowBounds(number(settings, "x"), number(settings, "y"),
                    number(settings, "width"), number(settings, "height"),
                    Boolean.parseBoolean(settings.getProperty(WINDOW + "maximized")));
            return bounds.width() > 0 && bounds.height() > 0 ? Optional.of(bounds) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Remembers where the window is, for the next start. */
    public void saveWindowBounds(WindowBounds bounds) {

        update(settings -> {
            settings.setProperty(WINDOW + "x", String.valueOf(bounds.x()));
            settings.setProperty(WINDOW + "y", String.valueOf(bounds.y()));
            settings.setProperty(WINDOW + "width", String.valueOf(bounds.width()));
            settings.setProperty(WINDOW + "height", String.valueOf(bounds.height()));
            settings.setProperty(WINDOW + "maximized", String.valueOf(bounds.maximized()));
        });
    }

    /** True if the book's counts under the chapter list were open when last left, as they are the first time. */
    public boolean bookCountsShown() {

        return flag(read().getProperty(BOOK_COUNTS_SHOWN), true);
    }

    /** Remembers whether the book's counts under the chapter list are open or collapsed. */
    public void saveBookCountsShown(boolean shown) {

        update(settings -> settings.setProperty(BOOK_COUNTS_SHOWN, String.valueOf(shown)));
    }

    /** How tall the Media tab's preview is, in pixels, as the author last dragged it. */
    public double mediaPreviewHeight() {

        return size(MEDIA_PREVIEW_HEIGHT, DEFAULT_MEDIA_PREVIEW_HEIGHT);
    }

    /** Remembers how tall the Media tab's preview is, in pixels. */
    public void saveMediaPreviewHeight(double height) {

        saveSize(MEDIA_PREVIEW_HEIGHT, height);
    }

    /** How wide the chapter list is, in pixels, as the author last dragged it. */
    public double sidebarWidth() {

        return size(SIDEBAR_WIDTH, DEFAULT_SIDEBAR_WIDTH);
    }

    /** Remembers how wide the chapter list is, in pixels. */
    public void saveSidebarWidth(double width) {

        saveSize(SIDEBAR_WIDTH, width);
    }

    /** The tab shown beside the chapter each time a book opens; the preview until the author pins another. */
    public ChapterTab openingTab() {

        return named(ChapterTab.class, read().getProperty(OPENING_TAB)).orElse(ChapterTab.PREVIEW);
    }

    /** Remembers {@code tab} as the one to show each time a book opens. */
    public void saveOpeningTab(ChapterTab tab) {

        update(settings -> settings.setProperty(OPENING_TAB, written(tab)));
    }

    /** {@code tag} as a language tag, if it names a language. */
    private static Optional<String> language(String tag) {

        if (tag == null)
            return Optional.empty();

        Locale locale = Locale.forLanguageTag(tag.strip());
        return locale.getLanguage().isEmpty() ? Optional.empty() : Optional.of(locale.toLanguageTag());
    }

    /** The constant of {@code type} named {@code name}, as {@link #written} wrote it, if there is one. */
    private static <E extends Enum<E>> Optional<E> named(Class<E> type, String name) {

        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equalsIgnoreCase(name))
                .findFirst();
    }

    /** A part's size, in pixels, stored under {@code key}; {@code fallback} if none is stored or it cannot be read. */
    private double size(String key, double fallback) {

        try {
            double size = Double.parseDouble(read().getProperty(key, "").strip());
            return Double.isFinite(size) && size > 0 ? size : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Remembers a part's size under {@code key}, in whole pixels. */
    private void saveSize(String key, double size) {

        update(settings -> settings.setProperty(key, String.valueOf(Math.round(size))));
    }

    /** {@code constant} as the file has it, in lower case, as {@code dark} for a preview theme. */
    private static String written(Enum<?> constant) {

        return constant.name().toLowerCase(Locale.ROOT);
    }

    /** {@code value} as true or false, as written in the file; {@code fallback} for anything else or nothing. */
    private static boolean flag(String value, boolean fallback) {

        if ("true".equals(value) || "false".equals(value))
            return Boolean.parseBoolean(value);

        return fallback;
    }

    /** {@code value} as a font size, if it is one of those Preferences offers. */
    private static Optional<Integer> fontSize(String value) {

        if (value == null)
            return Optional.empty();

        try {
            return Optional.of(Integer.parseInt(value.strip())).filter(Preferences.FONT_SIZES::contains);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** The window's value {@code name}, a finite number. @throws NumberFormatException if it is missing or not one */
    private static double number(Properties settings, String name) {

        String value = settings.getProperty(WINDOW + name);
        if (value == null)
            throw new NumberFormatException("No " + WINDOW + name);

        double number = Double.parseDouble(value.strip());
        if (!Double.isFinite(number))
            throw new NumberFormatException(WINDOW + name + " is " + value);

        return number;
    }

    /** The books listed in {@code settings}, in order; a path that cannot be read is skipped. */
    private RecentBooks recentBooks(Properties settings) {

        List<Path> books = new ArrayList<>();
        for (int i = 1; i <= RecentBooks.MAX; i++) {
            String book = settings.getProperty(RECENT + i);
            if (book == null || book.isBlank())
                continue;

            try {
                books.add(folder.path().resolve(book).normalize());
            } catch (InvalidPathException e) {
                log.warn("Skipping the recent book {}: not a path", book);
            }
        }

        return new RecentBooks(books);
    }

    /**
     * How {@code book} is written down: its absolute path, but relative to the settings folder when both are on the
     * same drive of a portable app, which keeps working when the drive's letter or mount point changes.
     */
    private String stored(Path book) {

        if (folder.portable() && sameDrive(book, folder.path()))
            return folder.path().toAbsolutePath().relativize(book.toAbsolutePath()).toString();

        return book.toAbsolutePath().toString();
    }

    private static boolean sameDrive(Path book, Path settings) {

        try {
            return Objects.equals(Files.getFileStore(book), Files.getFileStore(settings));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Applies {@code change} to the settings read afresh, so changes another window made meanwhile are kept; nothing if
     * they cannot be read, as the file would then lose every other setting.
     *
     * @return true if the change is saved
     */
    private boolean update(Consumer<Properties> change) {

        Optional<Properties> settings = saved();
        settings.ifPresent(change);

        return settings.map(this::write).orElse(false);
    }

    /** The settings as saved; none if there is no file yet or it cannot be read. */
    private Properties read() {

        return saved().orElseGet(Properties::new);
    }

    /** The settings as saved, none if there is no file yet; empty, and logged, if it cannot be read. */
    private Optional<Properties> saved() {

        try {
            return Optional.of(PropertiesFiles.readLeniently(folder.path().resolve(FILE)));
        } catch (UncheckedIOException e) {
            log.warn("Cannot read the settings in {}", folder.path(), e);
            return Optional.empty();
        }
    }

    /**
     * Writes {@code settings} whole, in place of the file, with the keys in order and no timestamp.
     *
     * @return true if they are written; false, and logged, if they cannot be
     */
    private boolean write(Properties settings) {

        try {
            Files.createDirectories(folder.path());
            PropertiesFiles.write(folder.path().resolve(FILE), settings, COMMENT);
            return true;
        } catch (IOException | UncheckedIOException e) {
            log.warn("Cannot save {} in {}", FILE, folder.path(), e);
            return false;
        }
    }

    /** Writes {@code text} whole as the file {@code name} of the settings folder, in place of the one there. */
    private void writeFile(String name, String text) {

        try {
            Files.createDirectories(folder.path());
            SafeFiles.writeString(folder.path().resolve(name), text);
        } catch (IOException e) {
            log.warn("Cannot save {} in {}", name, folder.path(), e);
        }
    }
}
