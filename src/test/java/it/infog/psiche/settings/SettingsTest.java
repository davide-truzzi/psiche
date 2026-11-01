package it.infog.psiche.settings;

import it.infog.psiche.settings.model.Preferences;
import it.infog.psiche.settings.model.RecentBooks;
import it.infog.psiche.settings.model.SettingsFolder;
import it.infog.psiche.settings.model.WindowBounds;
import it.infog.psiche.settings.model.enums.PreviewTheme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsTest {

    @TempDir
    Path tempDir;

    @Test
    void withoutAFileThereAreNoRecentBooks() {
        assertThat(settings().recentBooks().books()).isEmpty();
    }

    @Test
    void theLatestBookComesFirstAndEachBookOnce() throws IOException {
        Path alba = book("alba"), sera = book("sera"), notte = book("notte");
        Settings settings = settings();

        settings.bookOpened(alba);
        settings.bookOpened(sera);
        settings.bookOpened(notte);
        settings.bookOpened(alba);

        assertThat(settings.recentBooks().books()).containsExactly(alba, notte, sera);
    }

    @Test
    void aBookReachedThroughALinkIsTheSameBook() throws IOException {
        Path alba = book("alba");
        Path link = Files.createSymbolicLink(tempDir.resolve("link-to-alba"), alba);
        Settings settings = settings();

        settings.bookOpened(alba);
        settings.bookOpened(link);

        assertThat(settings.recentBooks().books()).containsExactly(alba);
    }

    @Test
    void keepsOnlyTheLatestTen() throws IOException {
        Settings settings = settings();
        List<Path> books = IntStream.rangeClosed(1, 12).mapToObj(i -> book("book-" + i)).toList();

        books.forEach(settings::bookOpened);

        assertThat(settings.recentBooks().books()).hasSize(RecentBooks.MAX)
                .startsWith(books.get(11)).endsWith(books.get(2)).doesNotContain(books.get(0), books.get(1));
    }

    @Test
    void aBookThatIsGoneStaysListedUntilTheListIsCleared() throws IOException {
        Path alba = book("alba");
        Settings settings = settings();
        settings.bookOpened(alba);
        Files.delete(alba);

        assertThat(settings.recentBooks().books()).containsExactly(alba);

        settings.clearRecentBooks();
        assertThat(settings.recentBooks().books()).isEmpty();
    }

    @Test
    void savedSettingsAreReadBackByTheNextRunAndASecondWindowKeepsTheFirstsChanges() throws IOException {
        Path alba = book("alba"), sera = book("sera");
        Settings first = settings();
        Settings second = settings();

        first.bookOpened(alba);
        second.bookOpened(sera);

        assertThat(settings().recentBooks().books()).containsExactly(sera, alba);
        assertThat(Files.readString(folder().path().resolve(Settings.FILE)))
                .startsWith("# Psiche Writer's settings").doesNotContain(".tmp");
        assertThat(Files.list(folder().path())).containsExactly(folder().path().resolve(Settings.FILE));
    }

    @Test
    void aDamagedFileReadsAsNoSettings() throws IOException {
        Files.createDirectories(folder().path());
        Files.writeString(folder().path().resolve(Settings.FILE), "recent.1=\\u00zz broken escape\n");

        assertThat(settings().recentBooks().books()).isEmpty();
    }

    @Test
    void aFolderThatCannotBeWrittenNeverStopsTheApp() throws IOException {
        Path locked = Files.createDirectory(tempDir.resolve("locked"));
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-xr-xr-x"));
        Settings settings = new Settings(new SettingsFolder(locked.resolve("psiche"), false));

        settings.bookOpened(book("alba"));

        assertThat(settings.recentBooks().books()).isEmpty();
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    @Test
    void aPortableAppRemembersBooksOnItsDriveRelativeToItsSettings() throws IOException {
        Path stick = Files.createDirectories(tempDir.resolve("stick"));
        Path settingsFolder = Files.createDirectories(stick.resolve("psiche/settings"));
        Path alba = Files.createDirectories(stick.resolve("books/alba"));

        new Settings(new SettingsFolder(settingsFolder, true)).bookOpened(alba);

        assertThat(Files.readString(settingsFolder.resolve(Settings.FILE))).contains("recent.1=../../books/alba");
        assertThat(new Settings(new SettingsFolder(settingsFolder, true)).recentBooks().books())
                .containsExactly(alba.toRealPath());
    }

    @Test
    void anAuthorWhoChoseNothingHasTheDefaultPreferences() {
        assertThat(settings().preferences())
                .isEqualTo(new Preferences(false, "en-US", "", PreviewTheme.DEFAULT))
                .isEqualTo(Preferences.defaults());
    }

    @Test
    void savedPreferencesAreReadBackAndLeaveTheRecentBooksAlone() throws IOException {
        Path alba = book("alba");
        Settings settings = settings();
        settings.bookOpened(alba);

        Preferences chosen = new Preferences(true, "it-IT", "Anna Bianchi", PreviewTheme.DARK);
        settings.savePreferences(chosen);

        assertThat(settings().preferences()).isEqualTo(chosen);
        assertThat(settings().recentBooks().books()).containsExactly(alba);
        assertThat(Files.readString(folder().path().resolve(Settings.FILE)))
                .contains("book.language=it-IT", "book.author=Anna Bianchi", "preview.theme=dark",
                        "startup.reopen-last-book=true");
    }

    @Test
    void aPreferenceThatCannotBeReadIsItsDefault() throws IOException {
        Files.createDirectories(folder().path());
        Files.writeString(folder().path().resolve(Settings.FILE),
                "book.language=\npreview.theme=sepia\nstartup.reopen-last-book=sometimes\n");

        assertThat(settings().preferences()).isEqualTo(Preferences.defaults());
    }

    @Test
    void theWindowIsWhereItWasLastTime() {
        assertThat(settings().windowBounds()).isEmpty();

        settings().saveWindowBounds(new WindowBounds(120, 80.5, 1280, 800, true));

        assertThat(settings().windowBounds()).contains(new WindowBounds(120, 80.5, 1280, 800, true));
    }

    @Test
    void aWindowThatCannotBeReadIsForgotten() throws IOException {
        Files.createDirectories(folder().path());
        Path file = folder().path().resolve(Settings.FILE);

        Files.writeString(file, "window.x=10\nwindow.y=10\nwindow.width=wide\nwindow.height=800\n");
        assertThat(settings().windowBounds()).isEmpty();

        Files.writeString(file, "window.x=10\nwindow.y=10\nwindow.width=NaN\nwindow.height=800\n");
        assertThat(settings().windowBounds()).isEmpty();

        Files.writeString(file, "window.x=10\nwindow.y=10\nwindow.width=0\nwindow.height=800\n");
        assertThat(settings().windowBounds()).isEmpty();

        Files.writeString(file, "window.x=10\nwindow.y=10\nwindow.height=800\n");
        assertThat(settings().windowBounds()).isEmpty();
    }

    private Settings settings() {
        return new Settings(folder());
    }

    private SettingsFolder folder() {
        return new SettingsFolder(tempDir.resolve("config/psiche"), false);
    }

    private Path book(String name) {
        try {
            return Files.createDirectories(tempDir.resolve("books").resolve(name)).toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
