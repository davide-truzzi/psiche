package it.infog.psiche.notes;

import it.infog.psiche.notes.model.BoardView;
import it.infog.psiche.notes.model.Note;
import it.infog.psiche.notes.model.NoteLayout;
import it.infog.psiche.notes.model.OrphanGroup;
import it.infog.psiche.notes.model.enums.NoteColor;
import it.infog.psiche.notes.model.enums.OrphanCause;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class NoteStoreTest {

    private static final LocalDateTime MORNING = LocalDateTime.of(2026, 11, 1, 9, 30, 0);
    private static final NoteLayout LAYOUT = new NoteLayout(40, 60, 220, 160, 1, NoteColor.PAPER);

    @TempDir
    Path tempDir;

    @Test
    void aNewNoteIsSavedAndReadBack() {
        NoteStore store = store(MORNING);

        Note note = store.create("01-alba", LAYOUT);
        store.saveText("01-alba", note.id(), "Marco lies about the letter.\nHe read it.");
        NoteLayout moved = new NoteLayout(300.5, 420, 260, 200, 7, NoteColor.SAGE);
        store.saveLayout("01-alba", note.id(), moved);

        assertThat(note.id()).matches("20261101-093000-[0-9a-f]{4}");
        assertThat(store(MORNING).notes("01-alba")).containsExactly(
                new Note(note.id(), "Marco lies about the letter.\nHe read it.", moved, false, Optional.empty()));
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
    void replacingAFileKeepsTheOldOneWithACopyOfTheNoteInDeletedNotes() throws IOException {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);
        store.saveText("01-alba", note.id(), "The castle.");
        store.attach("01-alba", note.id(), Files.writeString(tempDir.resolve("old.jpg"), "old"), (bytes, total) -> {});

        store.attach("01-alba", note.id(), Files.writeString(tempDir.resolve("new.jpg"), "new"), (bytes, total) -> {});

        assertThat(store.notes("01-alba")).singleElement().satisfies(kept ->
                assertThat(kept.attachment()).hasValueSatisfying(file -> assertThat(file).hasContent("new")));
        assertThat(store.orphans(List.of("01-alba"))).singleElement().satisfies(group -> {
            assertThat(group.cause()).isEqualTo(OrphanCause.NOTE_DELETED);
            assertThat(group.notes()).singleElement().satisfies(copy -> {
                assertThat(copy.id()).isNotEqualTo(note.id());
                assertThat(copy.text()).isEqualTo("The castle.");
                assertThat(copy.attachment()).hasValueSatisfying(file -> assertThat(file).hasContent("old"));
            });
        });
    }

    @Test
    void removingAFileKeepsTheNoteAndPutsTheFileInDeletedNotes() throws IOException {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);
        store.attach("01-alba", note.id(), Files.writeString(tempDir.resolve("map.pdf"), "map"), (bytes, total) -> {});

        store.removeAttachment("01-alba", note.id());

        assertThat(store.notes("01-alba")).singleElement().extracting(Note::attachment).isEqualTo(Optional.empty());
        assertThat(store.orphans(List.of("01-alba"))).singleElement().extracting(group -> group.notes().getFirst())
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
        assertThat(store.orphans(List.of("02-sera"))).singleElement().satisfies(group -> {
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
        assertThat(store.orphans(List.of("01-alba"))).singleElement().satisfies(group -> {
            assertThat(group.cause()).isEqualTo(OrphanCause.NOTE_DELETED);
            assertThat(group.notes()).extracting(Note::id).containsExactlyInAnyOrder(first.id(), second.id());
        });
    }

    @Test
    void aFileTheAuthorPutInAChaptersFolderKeepsTheFolder() throws IOException {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);
        Files.writeString(notesDir().resolve("01-alba").resolve("todo.txt"), "mine");

        store.binNote("01-alba", note.id());

        assertThat(notesDir().resolve("01-alba").resolve("todo.txt")).hasContent("mine");
    }

    @Test
    void aFolderLeftByAnOutsideRenameIsAnOrphanWithoutDate() {
        NoteStore store = store(MORNING);
        store.create("01-alba", LAYOUT);

        assertThat(store.orphans(List.of("01-aurora"))).singleElement().satisfies(group -> {
            assertThat(group.chapter()).isEqualTo("01-alba");
            assertThat(group.cause()).isEqualTo(OrphanCause.CHAPTER_GONE);
            assertThat(group.deletedAt()).isEmpty();
        });
    }

    @Test
    void aChapterWhoseNameLooksDeletedIsNoOrphan() {
        NoteStore store = store(MORNING);
        store.create("alba-del-2026-01-01-00-00-00", LAYOUT);

        assertThat(store.orphans(List.of("alba-del-2026-01-01-00-00-00"))).isEmpty();
    }

    @Test
    void orphansComeMostRecentlyDeletedFirstThenTheOthersByName() {
        store(MORNING).create("03-notte", LAYOUT);
        store(MORNING).create("01-alba", LAYOUT);
        store(MORNING).binChapter("01-alba");
        store(MORNING).create("02-sera", LAYOUT);
        store(MORNING.plusHours(2)).binChapter("02-sera");
        store(MORNING).create("00-gone", LAYOUT);

        assertThat(store(MORNING).orphans(List.of())).extracting(OrphanGroup::chapter)
                .containsExactly("02-sera", "01-alba", "00-gone", "03-notte");
    }

    @Test
    void theNotesOfALaterDeletionAreTheOnesARestoreOffers() {
        store(MORNING).create("01-alba", LAYOUT);
        store(MORNING).binChapter("01-alba");
        Note newer = store(MORNING).create("01-alba", LAYOUT);
        store(MORNING.plusDays(1)).binChapter("01-alba");
        Note deletedAlone = store(MORNING).create("01-alba", LAYOUT);
        store(MORNING.plusDays(2)).binNote("01-alba", deletedAlone.id());

        Optional<OrphanGroup> latest = store(MORNING).latestDeleted("01-alba");

        assertThat(latest).hasValueSatisfying(group -> {
            assertThat(group.deletedAt()).contains(MORNING.plusDays(1));
            assertThat(group.notes()).extracting(Note::id).containsExactly(newer.id());
        });
        assertThat(store(MORNING).latestDeleted("02-sera")).isEmpty();
    }

    @Test
    void restoringMovesTheNotesToTheChapterBesideTheOnesItHas() {
        NoteStore store = store(MORNING);
        Note deleted = store.create("01-alba", LAYOUT);
        store.binChapter("01-alba");
        Note kept = store.create("02-sera", LAYOUT);

        store.restoreTo(store.orphans(List.of("02-sera")).getFirst(), "02-sera");

        assertThat(store.notes("02-sera")).extracting(Note::id).containsExactlyInAnyOrder(deleted.id(), kept.id());
        assertThat(store.orphans(List.of("02-sera"))).isEmpty();
        assertThat(notesDir().resolve("01-alba-del-2026-11-01-09-30-00")).doesNotExist();
    }

    @Test
    void aBookHasOrphansOnlyWhileNotesAreKeptOutsideItsChapters() {
        NoteStore store = store(MORNING);
        assertThat(store.hasOrphans(List.of("01-alba"))).isFalse();

        Note note = store.create("01-alba", LAYOUT);
        assertThat(store.hasOrphans(List.of("01-alba"))).isFalse();
        assertThat(store.hasOrphans(List.of("02-sera"))).isTrue();

        store.binNote("01-alba", note.id());
        assertThat(store.hasOrphans(List.of("01-alba"))).isTrue();

        store.deletePermanently(store.orphans(List.of("01-alba")).getFirst());
        assertThat(store.hasOrphans(List.of("01-alba"))).isFalse();
    }

    @Test
    void aNoteDeletedAloneGoesBackToItsChapterWithItsFileAndPlace() throws IOException {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", new NoteLayout(300, 420, 260, 190, 4, NoteColor.SKY));
        store.saveText("01-alba", note.id(), "Lucia waits by the gate.");
        Files.write(notesDir().resolve("01-alba").resolve(note.id()).resolve("cancello.jpg"), new byte[] {1, 2});
        store.binNote("01-alba", note.id());
        OrphanGroup group = store.orphans(List.of("01-alba")).getFirst();

        store.restoreTo(group, "01-alba");

        assertThat(store.notes("01-alba")).singleElement().satisfies(restored -> {
            assertThat(restored.id()).isEqualTo(note.id());
            assertThat(restored.text()).isEqualTo("Lucia waits by the gate.");
            assertThat(restored.layout()).isEqualTo(new NoteLayout(300, 420, 260, 190, 4, NoteColor.SKY));
            assertThat(restored.attachment()).map(file -> file.getFileName().toString()).contains("cancello.jpg");
        });
        assertThat(store.hasOrphans(List.of("01-alba"))).isFalse();
    }

    @Test
    void deletingPermanentlyRemovesTheNotesAndTheirFiles() throws IOException {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);
        Files.write(notesDir().resolve("01-alba").resolve(note.id()).resolve("castello.jpg"), new byte[] {1});
        store.binNote("01-alba", note.id());

        store.deletePermanently(store.orphans(List.of()).getFirst());

        assertThat(store.orphans(List.of())).isEmpty();
        assertThat(notesDir()).isEmptyDirectory();
    }

    private NoteStore store(LocalDateTime now) {
        return new NoteStore(notesDir(), Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    private Path notesDir() {
        return tempDir.resolve("book/notes");
    }

    private static NoteLayout layout(int order) {
        return new NoteLayout(0, 0, 220, 160, order, NoteColor.PAPER);
    }
}
