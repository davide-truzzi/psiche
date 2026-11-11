package it.infog.psiche.notes;

import it.infog.psiche.notes.model.BoardView;
import it.infog.psiche.notes.model.Note;
import it.infog.psiche.notes.model.NoteLayout;
import it.infog.psiche.notes.model.NotePin;
import it.infog.psiche.notes.model.OrphanGroup;
import it.infog.psiche.notes.model.enums.NoteColor;
import it.infog.psiche.notes.model.enums.OrphanCause;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class NoteStoreTest extends BookNotesTest {

    @Test
    void aNewNoteIsSavedAndReadBack() {
        NoteStore store = store(MORNING);

        Note note = store.create("01-alba", LAYOUT);
        store.saveText("01-alba", note.id(), "Marco lies about the letter.\nHe read it.");
        NoteLayout moved = new NoteLayout(300.5, 420, 260, 200, 7, NoteColor.SAGE);
        store.saveLayout("01-alba", note.id(), moved);

        assertThat(note.id()).matches("20261101-093000-[0-9a-f]{4}");
        assertThat(store(MORNING).notes("01-alba")).containsExactly(
                new Note(note.id(), "Marco lies about the letter.\nHe read it.", moved, false, Optional.empty(),
                        Optional.empty()));
        assertThat(notesDir().resolve("01-alba").resolve(note.id()))
                .isDirectoryContaining("glob:**/note.md")
                .isDirectoryContaining("glob:**/note.properties");
    }

    @Test
    void notesComeLowestInTheStackFirst() {
        NoteStore store = store(MORNING);
        Note top = store.create("01-alba", layout(9));
        Note bottom = store.create("01-alba", layout(2));
        Note middle = store.create("01-alba", layout(5));

        assertThat(store.notes("01-alba")).extracting(Note::id).containsExactly(bottom.id(), middle.id(), top.id());
    }

    @Test
    void aChapterWithoutNotesHasNoneAndNoFolder() {
        assertThat(store(MORNING).notes("01-alba")).isEmpty();
        assertThat(notesDir()).doesNotExist();
    }

    @Test
    void aBookIsEmptyUntilItsFirstNote() {
        NoteStore store = store(MORNING);
        assertThat(store.isEmpty()).isTrue();

        store.create("01-alba", LAYOUT);

        assertThat(store.isEmpty()).isFalse();
    }

    @Test
    void savingLeavesNoTemporaryFilesBehind() throws IOException {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);

        store.saveText("01-alba", note.id(), "First");
        store.saveText("01-alba", note.id(), "Second");
        store.saveLayout("01-alba", note.id(), layout(3));

        try (Stream<Path> files = Files.list(notesDir().resolve("01-alba").resolve(note.id()))) {
            assertThat(files.map(file -> file.getFileName().toString())).containsExactlyInAnyOrder("note.md",
                    "note.properties");
        }
    }

    @Test
    void aDamagedNoteReadsWithItsDefaultsAndAnUnreadableOneIsLeftOut() throws IOException {
        NoteStore store = store(MORNING);
        Note damaged = store.create("01-alba", LAYOUT);
        Files.writeString(notesDir().resolve("01-alba").resolve(damaged.id()).resolve("note.properties"),
                "x=\\u00zz broken escape\n");
        Note unreadable = store.create("01-alba", LAYOUT);
        Path text = notesDir().resolve("01-alba").resolve(unreadable.id()).resolve("note.md");
        Files.delete(text);
        Files.createDirectory(text);

        assertThat(store.notes("01-alba")).extracting(Note::id, note -> note.layout().color())
                .containsExactly(tuple(damaged.id(), NoteColor.PAPER));
    }

    @Test
    void aNoteCarriesTheFileInItsFolderButNotATemporaryOne() throws IOException {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);
        Path folder = notesDir().resolve("01-alba").resolve(note.id());
        Files.writeString(folder.resolve(".note.md123.tmp"), "half written");
        Files.write(folder.resolve("castello.jpg"), new byte[] {1, 2, 3});

        assertThat(store.notes("01-alba")).singleElement()
                .extracting(Note::attachment).isEqualTo(Optional.of(folder.resolve("castello.jpg")));
    }

    @Test
    void attachingCopiesTheFileUnderItsNameAndLeavesTheOriginal() throws IOException {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);
        Path photo = Files.write(tempDir.resolve("castello.jpg"), new byte[3_000_000]);
        List<Long> progress = new java.util.ArrayList<>();

        Path attached = store.attach("01-alba", note.id(), photo, (bytes, total) -> progress.add(total - bytes));

        assertThat(attached).isEqualTo(notesDir().resolve("01-alba").resolve(note.id()).resolve("castello.jpg"));
        assertThat(attached).hasSameBinaryContentAs(photo);
        assertThat(photo).exists();
        assertThat(progress).isNotEmpty().endsWith(0L);
        assertThat(store.notes("01-alba")).singleElement().extracting(Note::attachment)
                .isEqualTo(Optional.of(attached));
    }

    @Test
    void aFileNamedLikeTheNotesOwnIsRenamed() throws IOException {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);
        store.saveText("01-alba", note.id(), "The plan.");
        Path file = Files.writeString(Files.createDirectories(tempDir.resolve("drafts")).resolve("note.md"), "Draft");

        Path attached = store.attach("01-alba", note.id(), file, (bytes, total) -> {});

        assertThat(attached.getFileName()).hasToString("note (1).md");
        assertThat(store.notes("01-alba")).singleElement().extracting(Note::text).isEqualTo("The plan.");
    }

    @Test
    void replacingAFileKeepsTheOldOneWithACopyOfTheNoteInDetachedNotes() throws IOException {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);
        store.saveText("01-alba", note.id(), "The castle.");
        store.attach("01-alba", note.id(), Files.writeString(tempDir.resolve("old.jpg"), "old"), (bytes, total) -> {});

        store.attach("01-alba", note.id(), Files.writeString(tempDir.resolve("new.jpg"), "new"), (bytes, total) -> {});

        assertThat(store.notes("01-alba")).singleElement().satisfies(kept ->
                assertThat(kept.attachment()).hasValueSatisfying(file -> assertThat(file).hasContent("new")));
        assertThat(store.detached().orphans(List.of("01-alba"))).singleElement().satisfies(group -> {
            assertThat(group.cause()).isEqualTo(OrphanCause.NOTE_DELETED);
            assertThat(group.notes()).singleElement().satisfies(copy -> {
                assertThat(copy.id()).isNotEqualTo(note.id());
                assertThat(copy.text()).isEqualTo("The castle.");
                assertThat(copy.attachment()).hasValueSatisfying(file -> assertThat(file).hasContent("old"));
            });
        });
    }

    @Test
    void removingAFileKeepsTheNoteAndPutsTheFileInDetachedNotes() throws IOException {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);
        store.attach("01-alba", note.id(), Files.writeString(tempDir.resolve("map.pdf"), "map"), (bytes, total) -> {});

        store.removeAttachment("01-alba", note.id());

        assertThat(store.notes("01-alba")).singleElement().extracting(Note::attachment).isEqualTo(Optional.empty());
        assertThat(store.detached().orphans(List.of("01-alba"))).singleElement()
                .extracting(group -> group.notes().getFirst())
                .satisfies(copy -> assertThat(copy.attachment()).hasValueSatisfying(file -> assertThat(file).exists()));
    }

    @Test
    void aFileGoneFromDiskIsStillNamedSoTheBoardCanSaySo() throws IOException {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);
        Path attached = store.attach("01-alba", note.id(), Files.writeString(tempDir.resolve("map.pdf"), "map"),
                (bytes, total) -> {});
        Files.delete(attached);

        assertThat(store.notes("01-alba")).singleElement().extracting(Note::attachment)
                .isEqualTo(Optional.of(attached));
    }

    @Test
    void theDuplicateOnImportTagIsSetAndCleared() {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);

        store.setDuplicateOnImport("01-alba", note.id(), true);
        assertThat(store.notes("01-alba")).singleElement().extracting(Note::duplicateOnImport).isEqualTo(true);

        store.setDuplicateOnImport("01-alba", note.id(), false);
        assertThat(store.notes("01-alba")).singleElement().extracting(Note::duplicateOnImport).isEqualTo(false);
    }

    @Test
    void aChapterWithNotesRemembersWhereItsBoardWasLookedAt() {
        NoteStore store = store(MORNING);
        store.saveBoardView("01-alba", new BoardView(0.5, 100, 800));
        assertThat(store.boardView("01-alba")).isEqualTo(BoardView.start());

        store.create("01-alba", LAYOUT);
        store.saveBoardView("01-alba", new BoardView(0.5, 100, 800));

        assertThat(store(MORNING).boardView("01-alba")).isEqualTo(new BoardView(0.5, 100, 800));
    }

    @Test
    void aChapterNameThatLeavesTheNotesFolderIsRefused() {
        assertThatThrownBy(() -> store(MORNING).create("../media", LAYOUT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renamingAChapterMovesItsNotesAndBoard() {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);
        store.saveBoardView("01-alba", new BoardView(2, 10, 20));

        store.rename("01-alba", "01-aurora");

        assertThat(store.notes("01-alba")).isEmpty();
        assertThat(store.notes("01-aurora")).extracting(Note::id).containsExactly(note.id());
        assertThat(store.boardView("01-aurora")).isEqualTo(new BoardView(2, 10, 20));
        assertThat(notesDir().resolve("01-alba")).doesNotExist();
    }

    @Test
    void renamingOntoAFolderLeftWithNotesKeepsBoth() {
        NoteStore store = store(MORNING);
        Note moving = store.create("01-alba", LAYOUT);
        Note left = store.create("01-aurora", LAYOUT);

        store.rename("01-alba", "01-aurora");

        assertThat(store.notes("01-aurora")).extracting(Note::id).containsExactlyInAnyOrder(moving.id(), left.id());
        assertThat(notesDir().resolve("01-alba")).doesNotExist();
    }

    @Test
    void renamingAChapterWithoutNotesDoesNothing() {
        store(MORNING).rename("01-alba", "01-aurora");

        assertThat(notesDir()).doesNotExist();
    }

    @Test
    void deletingAChapterKeepsItsNotesUnderItsNameAndTheTime() {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);

        store.binChapter("01-alba");

        assertThat(notesDir().resolve("01-alba-del-2026-11-01-09-30-00")).isDirectory();
        assertThat(store.notes("01-alba")).isEmpty();
        assertThat(store.detached().orphans(List.of("02-sera"))).singleElement().satisfies(group -> {
            assertThat(group.chapter()).isEqualTo("01-alba");
            assertThat(group.cause()).isEqualTo(OrphanCause.CHAPTER_DELETED);
            assertThat(group.deletedAt()).contains(MORNING);
            assertThat(group.notes()).extracting(Note::id).containsExactly(note.id());
        });
    }

    @Test
    void aDeletedNoteLeavesItsChapterAndItsLastOneTakesTheFolderAlong() throws IOException {
        NoteStore store = store(MORNING);
        Note first = store.create("01-alba", LAYOUT);
        Note second = store.create("01-alba", LAYOUT);

        store.binNote("01-alba", first.id());
        assertThat(store.notes("01-alba")).extracting(Note::id).containsExactly(second.id());

        store.binNote("01-alba", second.id());
        assertThat(notesDir().resolve("01-alba")).doesNotExist();
        assertThat(store.detached().orphans(List.of("01-alba"))).singleElement().satisfies(group -> {
            assertThat(group.cause()).isEqualTo(OrphanCause.NOTE_DELETED);
            assertThat(group.notes()).extracting(Note::id).containsExactlyInAnyOrder(first.id(), second.id());
        });
    }

    @Test
    void aNoteMovesWithItsTextAndFileToTheTopOfAnotherChaptersBoard() throws IOException {
        NoteStore store = store(MORNING);
        Note there = store.create("02-mezzogiorno", layout(4));
        Note note = store.create("01-alba", new NoteLayout(300, 420, 260, 200, 1, NoteColor.SAGE));
        store.saveText("01-alba", note.id(), "Marco lies.");
        store.attach("01-alba", note.id(), Files.writeString(tempDir.resolve("map.png"), "map"), (bytes, total) -> {});

        store.moveNote("01-alba", note.id(), "02-mezzogiorno");

        assertThat(notesDir().resolve("01-alba")).doesNotExist();
        assertThat(store.notes("02-mezzogiorno")).extracting(Note::id).containsExactly(there.id(), note.id());
        Note moved = store.notes("02-mezzogiorno").getLast();
        assertThat(moved.text()).isEqualTo("Marco lies.");
        assertThat(moved.layout()).isEqualTo(new NoteLayout(300, 420, 260, 200, 5, NoteColor.SAGE));
        assertThat(moved.attachment()).hasValueSatisfying(file -> assertThat(file).hasContent("map"));
    }

    @Test
    void aPinIsSavedAndReadBackAndUnpinningTakesItAway() {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);

        store.savePin("01-alba", note.id(), Optional.of(new NotePin(12, "Marco opened the letter")));
        Note pinned = store(MORNING).notes("01-alba").getFirst();
        store.savePin("01-alba", note.id(), Optional.empty());

        assertThat(pinned.pin()).contains(new NotePin(12, "Marco opened the letter"));
        assertThat(store.notes("01-alba").getFirst().pin()).isEmpty();
        assertThat(notesDir().resolve("01-alba").resolve(note.id()).resolve("note.properties"))
                .content().doesNotContain("pin");
    }

    @Test
    void aPinWithoutItsTextOrWithAWrongNumberIsNoPin() throws IOException {
        NoteStore store = store(MORNING);
        Note noText = store.create("01-alba", LAYOUT);
        Note wrongNumber = store.create("01-alba", LAYOUT);
        Path folder = notesDir().resolve("01-alba");
        Files.writeString(folder.resolve(noText.id()).resolve("note.properties"), "pinParagraph=3\n");
        Files.writeString(folder.resolve(wrongNumber.id()).resolve("note.properties"), "pinParagraph=x\npinText=A\n");

        assertThat(store.notes("01-alba")).extracting(Note::pin).containsOnly(Optional.empty());
    }

    @Test
    void aPinKeepsOnlyTheOpeningOfALongParagraph() {
        String line = "Marco opened the letter by the window, read it twice, and folded it back into its envelope.";

        assertThat(NotePin.of(4, line)).isEqualTo(new NotePin(4, line.substring(0, NotePin.TEXT_LENGTH)));
        assertThat(NotePin.of(4, "Short.")).isEqualTo(new NotePin(4, "Short."));
    }

    @Test
    void aNoteMovedToAnotherBoardLosesItsPin() {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);
        store.savePin("01-alba", note.id(), Optional.of(new NotePin(3, "Dawn.")));

        store.moveNote("01-alba", note.id(), NoteStore.BOOK);

        assertThat(store.notes(NoteStore.BOOK).getFirst().pin()).isEmpty();
    }

    @Test
    void aDetachedNoteLosesItsPin() {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);
        store.savePin("01-alba", note.id(), Optional.of(new NotePin(3, "Dawn.")));

        store.binNote("01-alba", note.id());

        assertThat(store.detached().orphans(List.of("01-alba"))).flatExtracting(OrphanGroup::notes)
                .extracting(Note::pin).containsExactly(Optional.empty());
    }

    @Test
    void aReplacedFilesCopyOfTheNoteIsUnpinnedWhileTheNoteKeepsItsPin() throws IOException {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);
        store.savePin("01-alba", note.id(), Optional.of(new NotePin(3, "Dawn.")));
        store.attach("01-alba", note.id(), Files.writeString(tempDir.resolve("map.png"), "map"), (bytes, total) -> {});

        store.removeAttachment("01-alba", note.id());

        assertThat(store.notes("01-alba").getFirst().pin()).contains(new NotePin(3, "Dawn."));
        assertThat(store.detached().orphans(List.of("01-alba"))).flatExtracting(OrphanGroup::notes)
                .extracting(Note::pin).containsExactly(Optional.empty());
    }

    @Test
    void theBookHasABoardOfItsOwnBesideTheChapters() {
        NoteStore store = store(MORNING);
        Note chapterNote = store.create("01-alba", LAYOUT);
        Note map = store.create(NoteStore.BOOK, layout(3));
        store.saveText(NoteStore.BOOK, map.id(), "World map");
        store.saveBoardView(NoteStore.BOOK, new BoardView(0.5, 100, 200));

        assertThat(store(MORNING).notes(NoteStore.BOOK)).extracting(Note::text).containsExactly("World map");
        assertThat(store.notes("01-alba")).extracting(Note::id).containsExactly(chapterNote.id());
        assertThat(store.boardView(NoteStore.BOOK)).isEqualTo(new BoardView(0.5, 100, 200));
        assertThat(store.boardView("01-alba")).isNotEqualTo(new BoardView(0.5, 100, 200));
        assertThat(notesDir().resolve("_book").resolve(map.id())).isDirectoryContaining("glob:**/note.md");
    }

    @Test
    void aNoteMovesFromAChapterToTheBookAndBackWithItsFile() throws IOException {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", new NoteLayout(300, 420, 260, 200, 1, NoteColor.SAGE));
        store.attach("01-alba", note.id(), Files.writeString(tempDir.resolve("map.png"), "map"), (bytes, total) -> {});

        store.moveNote("01-alba", note.id(), NoteStore.BOOK);

        assertThat(store.notes("01-alba")).isEmpty();
        assertThat(store.notes(NoteStore.BOOK)).singleElement()
                .satisfies(moved -> assertThat(moved.attachment()).hasValueSatisfying(file -> assertThat(file)
                        .hasContent("map")));

        store.moveNote(NoteStore.BOOK, note.id(), "02-mezzogiorno");

        assertThat(store.notes(NoteStore.BOOK)).isEmpty();
        assertThat(store.notes("02-mezzogiorno")).extracting(Note::id).containsExactly(note.id());
    }

    @Test
    void aNoteMovedToABoardWithItsIdTakesANewOne() throws IOException {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);
        Note other = store.create("01-alba", LAYOUT);
        Files.createDirectories(notesDir().resolve("03-sera").resolve(note.id()));

        store.moveNote("01-alba", note.id(), "03-sera");

        assertThat(store.notes("01-alba")).extracting(Note::id).containsExactly(other.id());
        // The folder already there reads as a note of its own, which keeps the id.
        assertThat(store.notes("03-sera")).extracting(Note::id).hasSize(2).containsOnlyOnce(note.id());
    }

    @Test
    void aFileTheAuthorPutInAChaptersFolderKeepsTheFolder() throws IOException {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);
        Files.writeString(notesDir().resolve("01-alba").resolve("todo.txt"), "mine");

        store.binNote("01-alba", note.id());

        assertThat(notesDir().resolve("01-alba").resolve("todo.txt")).hasContent("mine");
    }
}
