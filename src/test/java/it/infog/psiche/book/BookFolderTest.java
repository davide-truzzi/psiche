package it.infog.psiche.book;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.book.model.enums.PageMeasure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookFolderTest {

    @TempDir
    Path tempDir;

    @Test
    void opensDirectoryWithChaptersAndMediaAsBookNamedAfterFolder() throws IOException {
        Path root = book("the-salt-road");

        BookFolder book = BookFolder.open(root);

        assertThat(book.title()).isEqualTo("the-salt-road");
        assertThat(book.chaptersDir()).isEqualTo(root.resolve("chapters"));
    }

    @ParameterizedTest
    @CsvSource({
            "chapters, missing media/",
            "media,    missing chapters/",
    })
    void rejectsFolderMissingABookSubdirectory(String presentDir, String expectedMessage) throws IOException {
        Path root = Files.createDirectories(tempDir.resolve("draft").resolve(presentDir)).getParent();

        assertThatThrownBy(() -> BookFolder.open(root))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith("is not a book folder: " + expectedMessage);
    }

    @Test
    void rejectsFolderMissingBothSubdirectories() {
        assertThatThrownBy(() -> BookFolder.open(tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith("missing chapters/ and media/");
    }

    @Test
    void rejectsRegularFile() throws IOException {
        Path file = Files.writeString(tempDir.resolve("notes.md"), "# Notes");

        assertThatThrownBy(() -> BookFolder.open(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(file + " is not a directory");
    }

    @Test
    void listsOnlyMarkdownChaptersOrderedByFileName() throws IOException {
        Path root = book("novel");
        Path chapters = root.resolve("chapters");
        Files.writeString(chapters.resolve("02-the-crossing.md"), "");
        Files.writeString(chapters.resolve("01-departure.md"), "");
        Files.writeString(chapters.resolve("outline.txt"), "");
        Files.createDirectory(chapters.resolve("old-drafts.md"));

        assertThat(BookFolder.open(root).chapters())
                .extracting(Chapter::title)
                .containsExactly("01-departure", "02-the-crossing");
    }

    @Test
    void savedChapterReadsBackIdentically() throws IOException {
        Path root = book("novel");
        Chapter chapter = new Chapter(Files.writeString(root.resolve("chapters/01-arrivo.md"), ""));
        BookFolder book = BookFolder.open(root);
        String markdown = "# Arrivo\n\nPerché il treno è già partito?\n";

        book.write(chapter, markdown);

        assertThat(book.read(chapter)).isEqualTo(markdown);
    }

    @Test
    void createsBookFolderNamedAfterTitleWithChaptersAndMedia() {
        BookFolder book = BookFolder.create(tempDir, "  The Salt Road ", "en-US", "");

        assertThat(book.title()).isEqualTo("The Salt Road");
        assertThat(tempDir.resolve("The Salt Road/chapters")).isEmptyDirectory();
        assertThat(book.chapters()).isEmpty();
    }

    @Test
    void createsBookWithItsPropertiesAndTheDefaultStyle() {
        BookFolder book = BookFolder.create(tempDir, "  The Salt Road ", "en-US", "");

        assertThat(tempDir.resolve("The Salt Road/media")).isDirectoryContaining("glob:**/book.properties")
                .isDirectoryContaining("glob:**/style.css");
        assertThat(book.properties().title()).isEqualTo("The Salt Road");
    }

    @Test
    void aNewBookIsWrittenInTheLanguageGivenAndHasNoAuthorIfNoneIsGiven() {
        BookFolder book = BookFolder.create(tempDir, "La via del sale", "it-IT", "");

        assertThat(book.properties().language()).isEqualTo("it-IT");
        assertThat(BookFolder.open(book.root()).properties().language()).isEqualTo("it-IT");
        assertThat(book.properties().authors()).isEmpty();
    }

    @Test
    void aNewBookStartsWithTheAuthorGiven() {
        BookFolder book = BookFolder.create(tempDir, "La via del sale", "it-IT", "  Anna Bianchi ");

        assertThat(BookFolder.open(book.root()).properties().authors()).containsExactly("Anna Bianchi");
    }

    // Properties, cover and style

    @Test
    void unsavedPropertiesHaveDefaults() throws IOException {
        BookProperties properties = BookFolder.open(book("the-salt-road")).properties();

        assertThat(properties.title()).isEqualTo("the-salt-road");
        assertThat(properties.subtitle()).isEmpty();
        assertThat(properties.authors()).isEmpty();
        assertThat(properties.language()).isNotBlank();
        assertThat(properties.identifier()).startsWith("urn:uuid:");
    }

    @Test
    void savedPropertiesReadBackTheSame() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));
        BookProperties properties = new BookProperties("Città: una storia", "Parte prima = l'inizio",
                List.of("Name Surname", "Anna Bianchi"), "it-IT", "© 2026 Name Surname", "urn:uuid:1234");

        book.saveProperties(properties);

        assertThat(book.properties()).isEqualTo(properties);
        assertThat(book.mediaDir().resolve("book.properties")).content().contains("Città").doesNotContain("\\u");
    }

    @Test
    void theIdentifierStaysTheSameOnceSaved() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));
        book.saveProperties(book.properties());

        assertThat(book.properties().identifier()).isEqualTo(book.properties().identifier());
    }

    @Test
    void savingKeepsEntriesWriterDoesNotEdit() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));
        Files.writeString(book.mediaDir().resolve("book.properties"), "title=Novel\nseries=The Salt Road\n");

        book.saveProperties(book.properties());

        assertThat(book.mediaDir().resolve("book.properties")).content().contains("series=The Salt Road");
    }

    @Test
    void thePageMeasureFollowsTheLanguageUntilOneIsChosen() throws IOException {
        BookFolder book = BookFolder.open(book("romanzo"));
        Files.writeString(book.mediaDir().resolve("book.properties"), "language=it-IT\n");

        assertThat(book.pageMeasure()).isEqualTo(PageMeasure.CARTELLA);

        book.savePageMeasure(PageMeasure.MANUSCRIPT);

        assertThat(book.pageMeasure()).isEqualTo(PageMeasure.MANUSCRIPT);
        assertThat(book.mediaDir().resolve("book.properties")).content().contains("pageCount=manuscript");
    }

    @Test
    void thePageMeasureAndThePropertiesKeepEachOther() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));
        BookProperties properties = new BookProperties("Novel", "", List.of("Anna Bianchi"), "en-GB", "",
                "urn:uuid:1234");
        book.saveProperties(properties);

        book.savePageMeasure(PageMeasure.FEUILLET);
        book.saveProperties(properties);

        assertThat(book.pageMeasure()).isEqualTo(PageMeasure.FEUILLET);
        assertThat(book.properties()).isEqualTo(properties);
    }

    @Test
    void anUnknownPageMeasureFallsBackToTheLanguage() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));
        Files.writeString(book.mediaDir().resolve("book.properties"), "language=fr-FR\npageCount=folio\n");

        assertThat(book.pageMeasure()).isEqualTo(PageMeasure.FEUILLET);
    }

    @Test
    void savingTwiceWritesTheSameFile() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));
        book.saveProperties(book.properties());
        String first = Files.readString(book.mediaDir().resolve("book.properties"));

        book.saveProperties(book.properties());

        assertThat(book.mediaDir().resolve("book.properties")).hasContent(first);
    }

    @Test
    void refusesABlankTitle() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));
        BookProperties untitled = new BookProperties(" ", "", List.of(), "en", "", "urn:uuid:1234");

        assertThatThrownBy(() -> book.saveProperties(untitled)).isInstanceOf(IllegalArgumentException.class);
        assertThat(book.mediaDir()).isEmptyDirectory();
    }

    @Test
    void savingPropertiesNeverOverwritesTheAuthorsStyle() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));
        Path style = Files.writeString(book.mediaDir().resolve("style.css"), "p { text-indent: 0; }");

        book.saveProperties(book.properties());

        assertThat(style).hasContent("p { text-indent: 0; }");
    }

    @Test
    void aNewCoverReplacesOneOfTheOtherType() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));
        Path jpeg = Files.writeString(tempDir.resolve("Front Cover.JPEG"), "jpeg");
        Path png = Files.writeString(tempDir.resolve("cover-final.png"), "png");

        book.setCover(jpeg);
        assertThat(book.cover()).contains(book.mediaDir().resolve("cover.jpg"));

        book.setCover(png);
        assertThat(book.cover()).contains(book.mediaDir().resolve("cover.png"));
        assertThat(book.mediaDir().resolve("cover.jpg")).doesNotExist();
        assertThat(book.mediaDir().resolve("cover.png")).hasContent("png");
    }

    @Test
    void refusesACoverThatIsNotJpegOrPng() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));
        Path gif = Files.writeString(tempDir.resolve("cover.gif"), "gif");

        assertThatThrownBy(() -> book.setCover(gif)).isInstanceOf(IllegalArgumentException.class);
        assertThat(book.cover()).isEmpty();
    }

    @Test
    void removesTheCover() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));
        book.setCover(Files.writeString(tempDir.resolve("cover.png"), "png"));

        book.removeCover();

        assertThat(book.cover()).isEmpty();
    }

    @Test
    void refusesToCreateOverAnExistingFolder() throws IOException {
        Path existing = Files.createDirectory(tempDir.resolve("novel"));
        Files.writeString(existing.resolve("notes.md"), "keep me");

        assertThatThrownBy(() -> BookFolder.create(tempDir, "novel", "en-US", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(existing + " already exists");
        assertThat(existing).isDirectoryContaining(path -> path.endsWith("notes.md"));
        assertThat(existing.resolve("chapters")).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", ".", "..", "part/one", "part\\one"})
    void refusesTitleThatIsNotASingleFolderName(String title) {
        assertThatThrownBy(() -> BookFolder.create(tempDir, title, "en-US", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void createsEmptyChapterThatIsListedInFileNameOrder() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));
        Files.writeString(book.chaptersDir().resolve("02-the-crossing.md"), "The sea was calm.");

        Chapter created = book.createChapter(" 01-departure ");

        assertThat(created.file()).isEqualTo(book.chaptersDir().resolve("01-departure.md"));
        assertThat(created.title()).isEqualTo("01-departure");
        assertThat(book.read(created)).isEmpty();
        assertThat(book.chapters()).extracting(Chapter::title).containsExactly("01-departure", "02-the-crossing");
    }

    @Test
    void doesNotDoubleTheExtensionWhenTitleAlreadyHasIt() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));

        Chapter created = book.createChapter("01-departure.md");

        assertThat(created.file()).isEqualTo(book.chaptersDir().resolve("01-departure.md"));
    }

    @Test
    void refusesToCreateChapterOverAnExistingOne() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));
        Path existing = Files.writeString(book.chaptersDir().resolve("01-departure.md"), "It began at dawn.");

        assertThatThrownBy(() -> book.createChapter("01-departure"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Chapter 01-departure.md already exists");
        assertThat(existing).hasContent("It began at dawn.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", ".md", ".", "..", "part/one", "part\\one", "../escape"})
    void refusesChapterTitleThatIsNotASingleFileName(String title) throws IOException {
        BookFolder book = BookFolder.open(book("novel"));

        assertThatThrownBy(() -> book.createChapter(title)).isInstanceOf(IllegalArgumentException.class);
        assertThat(book.chaptersDir()).isEmptyDirectory();
        assertThat(book.root()).isDirectoryNotContaining(path -> path.toString().endsWith(".md"));
    }

    @Test
    void renamesAChapterKeepingItsText() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));
        Chapter chapter = book.createChapter("01-departure");
        book.write(chapter, "It began at dawn.");

        Chapter renamed = book.renameChapter(chapter, " 01-the-departure ");

        assertThat(renamed.file()).isEqualTo(book.chaptersDir().resolve("01-the-departure.md"));
        assertThat(book.read(renamed)).isEqualTo("It began at dawn.");
        assertThat(book.chapters()).containsExactly(renamed);
    }

    @Test
    void renamingToTheSameTitleChangesNothing() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));
        Chapter chapter = book.createChapter("01-departure");

        assertThat(book.renameChapter(chapter, "01-departure.md")).isEqualTo(chapter);
        assertThat(book.chapters()).containsExactly(chapter);
    }

    @Test
    void renamingOnlyTheLetterCaseRenamesTheFile() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));
        Chapter chapter = book.createChapter("01-departure");

        Chapter renamed = book.renameChapter(chapter, "01-Departure");

        assertThat(book.chapters()).extracting(Chapter::title).containsExactly("01-Departure");
        assertThat(renamed.title()).isEqualTo("01-Departure");
    }

    @Test
    void refusesToRenameOverAnotherChapter() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));
        Chapter chapter = book.createChapter("01-departure");
        Chapter other = book.createChapter("02-crossing");

        assertThatThrownBy(() -> book.renameChapter(chapter, "02-crossing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Chapter 02-crossing.md already exists");
        assertThat(book.chapters()).containsExactly(chapter, other);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", ".md", "..", "part/one", "../escape"})
    void refusesToRenameToATitleThatIsNotASingleFileName(String title) throws IOException {
        BookFolder book = BookFolder.open(book("novel"));
        Chapter chapter = book.createChapter("01-departure");

        assertThatThrownBy(() -> book.renameChapter(chapter, title)).isInstanceOf(IllegalArgumentException.class);
        assertThat(book.chapters()).containsExactly(chapter);
    }

    @Test
    void deletesAChapter() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));
        Chapter chapter = book.createChapter("01-departure");
        Chapter other = book.createChapter("02-crossing");

        book.deleteChapter(chapter);

        assertThat(book.chapters()).containsExactly(other);
    }

    @Test
    void restoresAChapterWithItsTextButNotOverAnExistingOne() throws IOException {
        BookFolder book = BookFolder.open(book("novel"));

        Chapter restored = book.restoreChapter("02-crossing", "The sea was calm.");

        assertThat(book.read(restored)).isEqualTo("The sea was calm.");
        assertThatThrownBy(() -> book.restoreChapter("02-crossing", "Another text."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Chapter 02-crossing.md already exists");
        assertThat(book.read(restored)).isEqualTo("The sea was calm.");
    }

    private Path book(String name) throws IOException {
        Path root = tempDir.resolve(name);
        Files.createDirectories(root.resolve("chapters"));
        Files.createDirectories(root.resolve("media"));
        return root;
    }
}
