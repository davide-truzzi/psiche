package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.book.Chapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.collections.SetChangeListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DraftsTest {

    @TempDir
    Path root;

    BookFolder book;
    Chapter departure;
    Chapter crossing;
    Chapter storm;
    Drafts drafts = new Drafts();

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(root.resolve("media"));
        Files.createDirectories(root.resolve("chapters"));
        book = BookFolder.open(root);
        departure = book.createChapter("01-departure");
        crossing = book.createChapter("02-the-crossing");
        storm = book.createChapter("03-the-storm");
    }

    @Test
    void keepsTheLatestTextPerChapter() {
        drafts.put(departure, "It began");
        drafts.put(departure, "It began at dawn");

        assertThat(drafts.text(departure)).contains("It began at dawn");
        assertThat(drafts.text(crossing)).isEmpty();
        assertThat(drafts.chapters()).containsExactly(departure);
    }

    @Test
    void notifiesOnlyWhenAChapterGainsOrLosesItsDraft() {
        List<String> changes = new ArrayList<>();
        drafts.chapters().addListener((SetChangeListener<Chapter>) change ->
                changes.add((change.wasAdded() ? "+" : "-") + (change.wasAdded()
                        ? change.getElementAdded() : change.getElementRemoved()).title()));

        drafts.put(departure, "It");
        drafts.put(departure, "It began");
        drafts.put(departure, "It began at dawn");
        drafts.remove(departure);

        assertThat(changes).containsExactly("+01-departure", "-01-departure");
    }

    @Test
    void keepsNotifyingAfterGarbageCollection() {
        List<Chapter> added = new ArrayList<>();
        drafts.chapters().addListener((SetChangeListener<Chapter>) change -> {
            if (change.wasAdded()) {
                added.add(change.getElementAdded());
            }
        });
        // The read-only view forwards changes through a weak listener; it must outlive collections like any field.
        WeakReference<Object> canary = new WeakReference<>(new Object());
        for (int attempt = 0; attempt < 50 && canary.get() != null; attempt++) {
            System.gc();
        }

        drafts.put(departure, "It began at dawn");

        assertThat(canary.get()).as("garbage collection ran").isNull();
        assertThat(added).containsExactly(departure);
    }

    @Test
    void listsChaptersInBookOrder() {
        drafts.put(storm, "Thunder.");
        drafts.put(departure, "Dawn.");

        assertThat(drafts.sortedChapters()).containsExactly(departure, storm);
    }

    @Test
    void saveAllWritesEveryDraftAndForgetsThem() {
        drafts.put(departure, "It began at dawn.");
        drafts.put(storm, "Thunder rolled in.");

        drafts.saveAll(book);

        assertThat(book.read(departure)).isEqualTo("It began at dawn.");
        assertThat(book.read(storm)).isEqualTo("Thunder rolled in.");
        assertThat(book.read(crossing)).isEmpty();
        assertThat(drafts.isEmpty()).isTrue();
        assertThat(drafts.chapters()).isEmpty();
    }

    @Test
    void saveAllKeepsTheDraftOfAChapterThatCannotBeWrittenAndSavesTheOthers() throws IOException {
        // A directory where the chapter file should be makes writing it fail.
        Files.delete(crossing.file());
        Files.createDirectory(crossing.file());
        drafts.put(departure, "It began at dawn.");
        drafts.put(crossing, "The sea was calm.");
        drafts.put(storm, "Thunder rolled in.");

        assertThatThrownBy(() -> drafts.saveAll(book))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessage("Cannot save " + crossing.file());

        assertThat(book.read(departure)).isEqualTo("It began at dawn.");
        assertThat(book.read(storm)).isEqualTo("Thunder rolled in.");
        assertThat(drafts.sortedChapters()).containsExactly(crossing);
        assertThat(drafts.text(crossing)).contains("The sea was calm.");
    }

    @Test
    void clearDiscardsEveryDraftWithoutWriting() {
        drafts.put(departure, "It began at dawn.");

        drafts.clear();

        assertThat(drafts.isEmpty()).isTrue();
        assertThat(drafts.chapters()).isEmpty();
        assertThat(book.read(departure)).isEmpty();
    }
}
