package it.infog.psiche.book;

import it.infog.psiche.book.model.ChapterPlace;
import it.infog.psiche.book.model.enums.NotesBoard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BookStateTest {

    @TempDir
    Path tempDir;

    @Test
    void everyBookGetsTheFileAsItIsCreatedOrFirstOpened() throws IOException {
        BookFolder created = BookFolder.create(tempDir, "Lorem", "en-US", "");
        Path old = Files.createDirectories(tempDir.resolve("old/chapters"));
        Files.createDirectories(tempDir.resolve("old/media"));

        BookState.of(BookFolder.open(old.getParent()));

        assertThat(created.root().resolve("psiche.properties")).hasContent("""
                # Where you left each chapter and the notes, kept by Psiche Writer
                format=1""");
        assertThat(tempDir.resolve("old/psiche.properties")).exists();
    }

    @Test
    void aChapterReopensWhereItWasLeftEvenAfterARestart() throws IOException {
        BookFolder book = BookFolder.create(tempDir, "Lorem", "en-US", "");
        Chapter alba = book.createChapter("01 alba");
        Chapter notte = book.createChapter("02 notte");

        BookState.of(book).savePlace(alba, new ChapterPlace(1234, 45));
        BookState again = BookState.of(book);

        assertThat(again.place(alba)).contains(new ChapterPlace(1234, 45));
        assertThat(again.place(notte)).isEmpty();
    }

    @Test
    void aRenamedChapterKeepsItsPlaceAndADeletedOneLosesIt() throws IOException {
        BookFolder book = BookFolder.create(tempDir, "Lorem", "en-US", "");
        Chapter alba = book.createChapter("01 alba");
        Chapter notte = book.createChapter("02 notte");
        BookState state = BookState.of(book);
        state.savePlace(alba, new ChapterPlace(10, 1));
        state.savePlace(notte, new ChapterPlace(20, 2));

        Chapter dawn = new Chapter(Files.move(alba.file(), book.chaptersDir().resolve("01 dawn.md")));
        state.rename(alba, dawn);
        Files.delete(notte.file());
        state.savePlace(notte, new ChapterPlace(30, 3));

        BookState again = BookState.of(book);
        assertThat(again.place(dawn)).contains(new ChapterPlace(10, 1));
        assertThat(again.place(alba)).isEmpty();
        assertThat(again.place(notte)).isEmpty();
        assertThat(Files.readString(book.root().resolve("psiche.properties"))).doesNotContain("alba", "notte");
    }

    @Test
    void theNotesBoardLookedAtLastIsRememberedTheChaptersAtFirst() throws IOException {
        BookFolder book = BookFolder.create(tempDir, "Lorem", "en-US", "");
        assertThat(BookState.of(book).notesBoard()).isEqualTo(NotesBoard.CHAPTER);

        BookState.of(book).saveNotesBoard(NotesBoard.BOOK);

        assertThat(BookState.of(book).notesBoard()).isEqualTo(NotesBoard.BOOK);
        assertThat(Files.readString(book.root().resolve("psiche.properties"))).contains("notes.board=book");
    }

    @Test
    void aDamagedPlaceIsNoPlace() throws IOException {
        BookFolder book = BookFolder.create(tempDir, "Lorem", "en-US", "");
        Chapter alba = book.createChapter("01 alba");
        Files.writeString(book.root().resolve("psiche.properties"),
                "chapter.01\\ alba.caret=x\nchapter.01\\ alba.top=2\n");

        assertThat(BookState.of(book).place(alba)).isEmpty();
    }
}
