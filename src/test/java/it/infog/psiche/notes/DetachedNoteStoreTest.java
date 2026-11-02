package it.infog.psiche.notes;

import it.infog.psiche.notes.model.Note;
import it.infog.psiche.notes.model.NoteLayout;
import it.infog.psiche.notes.model.OrphanGroup;
import it.infog.psiche.notes.model.enums.NoteColor;
import it.infog.psiche.notes.model.enums.OrphanCause;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DetachedNoteStoreTest extends BookNotesTest {

    @Test
    void aFolderLeftByAnOutsideRenameIsAnOrphanWithoutDate() {
        NoteStore store = store(MORNING);
        store.create("01-alba", LAYOUT);

        assertThat(store.detached().orphans(List.of("01-aurora"))).singleElement().satisfies(group -> {
            assertThat(group.chapter()).isEqualTo("01-alba");
            assertThat(group.cause()).isEqualTo(OrphanCause.CHAPTER_GONE);
            assertThat(group.deletedAt()).isEmpty();
        });
    }

    @Test
    void aChapterWhoseNameLooksDeletedIsNoOrphan() {
        NoteStore store = store(MORNING);
        store.create("alba-del-2026-01-01-00-00-00", LAYOUT);

        assertThat(store.detached().orphans(List.of("alba-del-2026-01-01-00-00-00"))).isEmpty();
    }

    @Test
    void orphansComeMostRecentlyDeletedFirstThenTheOthersByName() {
        store(MORNING).create("03-notte", LAYOUT);
        store(MORNING).create("01-alba", LAYOUT);
        store(MORNING).binChapter("01-alba");
        store(MORNING).create("02-sera", LAYOUT);
        store(MORNING.plusHours(2)).binChapter("02-sera");
        store(MORNING).create("00-gone", LAYOUT);

        assertThat(store(MORNING).detached().orphans(List.of())).extracting(OrphanGroup::chapter)
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

        Optional<OrphanGroup> latest = store(MORNING).detached().latestDeleted("01-alba");

        assertThat(latest).hasValueSatisfying(group -> {
            assertThat(group.deletedAt()).contains(MORNING.plusDays(1));
            assertThat(group.notes()).extracting(Note::id).containsExactly(newer.id());
        });
        assertThat(store(MORNING).detached().latestDeleted("02-sera")).isEmpty();
    }

    @Test
    void restoringMovesTheNotesToTheChapterBesideTheOnesItHas() {
        NoteStore store = store(MORNING);
        Note deleted = store.create("01-alba", LAYOUT);
        store.binChapter("01-alba");
        Note kept = store.create("02-sera", LAYOUT);

        store.detached().restoreTo(store.detached().orphans(List.of("02-sera")).getFirst(), "02-sera");

        assertThat(store.notes("02-sera")).extracting(Note::id).containsExactlyInAnyOrder(deleted.id(), kept.id());
        assertThat(store.detached().orphans(List.of("02-sera"))).isEmpty();
        assertThat(notesDir().resolve("01-alba-del-2026-11-01-09-30-00")).doesNotExist();
    }

    @Test
    void aBookHasOrphansOnlyWhileNotesAreKeptOutsideItsChapters() {
        NoteStore store = store(MORNING);
        assertThat(store.detached().hasOrphans(List.of("01-alba"))).isFalse();

        Note note = store.create("01-alba", LAYOUT);
        assertThat(store.detached().hasOrphans(List.of("01-alba"))).isFalse();
        assertThat(store.detached().hasOrphans(List.of("02-sera"))).isTrue();

        store.binNote("01-alba", note.id());
        assertThat(store.detached().hasOrphans(List.of("01-alba"))).isTrue();

        store.detached().deletePermanently(store.detached().orphans(List.of("01-alba")).getFirst());
        assertThat(store.detached().hasOrphans(List.of("01-alba"))).isFalse();
    }

    @Test
    void aNoteDeletedAloneGoesBackToItsChapterWithItsFileAndPlace() throws IOException {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", new NoteLayout(300, 420, 260, 190, 4, NoteColor.SKY));
        store.saveText("01-alba", note.id(), "Lucia waits by the gate.");
        Files.write(notesDir().resolve("01-alba").resolve(note.id()).resolve("cancello.jpg"), new byte[] {1, 2});
        store.binNote("01-alba", note.id());
        OrphanGroup group = store.detached().orphans(List.of("01-alba")).getFirst();

        store.detached().restoreTo(group, "01-alba");

        assertThat(store.notes("01-alba")).singleElement().satisfies(restored -> {
            assertThat(restored.id()).isEqualTo(note.id());
            assertThat(restored.text()).isEqualTo("Lucia waits by the gate.");
            assertThat(restored.layout()).isEqualTo(new NoteLayout(300, 420, 260, 190, 4, NoteColor.SKY));
            assertThat(restored.attachment()).map(file -> file.getFileName().toString()).contains("cancello.jpg");
        });
        assertThat(store.detached().hasOrphans(List.of("01-alba"))).isFalse();
    }

    @Test
    void deletingPermanentlyRemovesTheNotesAndTheirFiles() throws IOException {
        NoteStore store = store(MORNING);
        Note note = store.create("01-alba", LAYOUT);
        Files.write(notesDir().resolve("01-alba").resolve(note.id()).resolve("castello.jpg"), new byte[] {1});
        store.binNote("01-alba", note.id());

        store.detached().deletePermanently(store.detached().orphans(List.of()).getFirst());

        assertThat(store.detached().orphans(List.of())).isEmpty();
        assertThat(notesDir()).isEmptyDirectory();
    }
}
