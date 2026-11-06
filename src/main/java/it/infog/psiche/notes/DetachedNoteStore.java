package it.infog.psiche.notes;

import it.infog.psiche.common.ChapterFolders;
import it.infog.psiche.notes.model.Note;
import it.infog.psiche.notes.model.OrphanGroup;
import it.infog.psiche.notes.model.enums.OrphanCause;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static it.infog.psiche.common.ChapterFolders.cause;
import static it.infog.psiche.common.ChapterFolders.deletedAt;
import static it.infog.psiche.common.ChapterFolders.deletedChapter;
import static it.infog.psiche.common.ChapterFolders.hasCause;
import static it.infog.psiche.common.ChapterFolders.isDeletedFrom;
import static it.infog.psiche.common.ChapterFolders.markCause;
import static it.infog.psiche.common.SafeFiles.deleteTree;
import static it.infog.psiche.notes.NoteFolders.chapterFolder;
import static it.infog.psiche.notes.NoteFolders.moveNotes;
import static it.infog.psiche.notes.NoteFolders.readNotes;
import static it.infog.psiche.notes.NoteFolders.subfolders;

/**
 * The notes off their boards, which Detached notes shows: those deleted in Psiche, alone or with their chapter, and
 * those whose chapter the book no longer has. A deleted note, or the notes of a deleted chapter, are kept in a deleted
 * folder of {@code notes/}, as {@link it.infog.psiche.common.ChapterFolders} names them; its notes are orphans, as are
 * those of a folder whose chapter was renamed or deleted outside Psiche. Get it from {@link NoteStore#detached()},
 * which keeps notes here as they are deleted.
 */
public final class DetachedNoteStore {

    // In a deleted folder: what was deleted, a note on its own or its chapter.
    private static final String NOTE_CAUSE = "note";
    private static final String CHAPTER_CAUSE = "chapter";

    private final Path root;
    private final Clock clock;

    /**
     * @param root  the book's {@code notes/} folder, which may not exist yet
     * @param clock the time deleted folders are named after, in its zone
     */
    DetachedNoteStore(Path root, Clock clock) {

        this.root = root;
        this.clock = clock;
    }

    // Keeping what is deleted, for NoteStore

    /** The folder that keeps what is deleted from {@code chapter} now: named after it and the time. */
    Path deletedFolder(String chapter) {

        return ChapterFolders.deletedFolder(root, chapter, clock);
    }

    /** Marks {@code folder} as holding notes deleted on their own, unless it says why already. */
    void markNoteDeleted(Path folder) {

        if (!hasCause(folder))
            markCause(folder, NOTE_CAUSE);
    }

    /** Marks {@code folder} as holding the notes of a deleted chapter, the notes deleted alone before included. */
    void markChapterDeleted(Path folder) {

        markCause(folder, CHAPTER_CAUSE);
    }

    // What Detached notes shows

    /**
     * The folders of notes that belong to none of {@code chapters}: deleted in Psiche, or left behind by a chapter
     * renamed or deleted outside it. The most recently deleted come first, then the others by name.
     */
    public List<OrphanGroup> orphans(Collection<String> chapters) {

        return orphanFolders(chapters).stream()
                .map(this::orphan)
                .filter(group -> !group.notes().isEmpty())
                .sorted(Comparator.comparing((OrphanGroup group) -> group.deletedAt().orElse(LocalDateTime.MIN))
                        .reversed()
                        .thenComparing(OrphanGroup::chapter))
                .toList();
    }

    /**
     * True if notes are kept in a folder that belongs to none of {@code chapters}, as {@link #orphans} would list them.
     * Only folders are looked at, not the notes in them, so it is quick enough to ask whenever the chapters change.
     */
    public boolean hasOrphans(Collection<String> chapters) {

        return orphanFolders(chapters).stream().anyMatch(folder -> !subfolders(folder).isEmpty());
    }

    /** The notes of {@code chapter} as it was last deleted, if any are kept; a restored chapter offers them back. */
    public Optional<OrphanGroup> latestDeleted(String chapter) {

        return folders(folder -> isDeletedFrom(folder, chapter)).stream()
                .map(this::orphan)
                .filter(group -> group.cause() == OrphanCause.CHAPTER_DELETED)
                .max(Comparator.comparing(group -> group.deletedAt().orElse(LocalDateTime.MIN)));
    }

    /**
     * Moves the notes of {@code group} to {@code chapter}'s board, and removes the group's folder once it is empty. A
     * note the board already has under the same id stays where it was, so nothing is lost.
     */
    public void restoreTo(OrphanGroup group, String chapter) {

        try {
            moveNotes(group.folder(), chapterFolder(root, chapter));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot move the notes of " + group.folder() + " to " + chapter, e);
        }
    }

    /** Deletes the notes of {@code group} and their files for good: they are not in Git, so they cannot come back. */
    public void deletePermanently(OrphanGroup group) {

        deleteTree(group.folder());
    }

    /** The folders in {@code notes/} named after none of {@code chapters}, nor the book's own board. */
    private List<Path> orphanFolders(Collection<String> chapters) {

        Set<String> names = Set.copyOf(chapters);
        return folders(folder -> {
            String name = folder.getFileName().toString();
            return !names.contains(name) && !name.equals(NoteStore.BOOK);
        });
    }

    /** The folders directly in {@code notes/} that {@code wanted} accepts; none if there is no {@code notes/}. */
    private List<Path> folders(Predicate<Path> wanted) {

        return subfolders(root).stream().filter(wanted).toList();
    }

    private OrphanGroup orphan(Path folder) {

        List<Note> notes = readNotes(folder);
        Optional<LocalDateTime> at = deletedAt(folder);
        if (at.isEmpty())
            return new OrphanGroup(folder, folder.getFileName().toString(), OrphanCause.CHAPTER_GONE, at, notes);

        // Folders deleted with a note say so; any other deleted folder went with its chapter.
        OrphanCause orphanCause = cause(folder).equals(NOTE_CAUSE) ? OrphanCause.NOTE_DELETED
                : OrphanCause.CHAPTER_DELETED;
        return new OrphanGroup(folder, deletedChapter(folder).orElseThrow(), orphanCause, at, notes);
    }
}
