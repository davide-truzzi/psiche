package it.infog.psiche.media;

import it.infog.psiche.media.model.DetachedImages;
import it.infog.psiche.media.model.enums.DetachCause;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static it.infog.psiche.common.ChapterFolders.cause;
import static it.infog.psiche.common.ChapterFolders.deletedAt;
import static it.infog.psiche.common.ChapterFolders.deletedChapter;
import static it.infog.psiche.common.ChapterFolders.isDeletedFrom;
import static it.infog.psiche.common.SafeFiles.deleteTree;

/**
 * The images off their chapters, which Detached images shows: those detached in Psiche, alone or with their deleted
 * chapter, and those in a folder of {@code media/} named after a chapter the book no longer has, renamed or deleted
 * outside Psiche. Get it from {@link ChapterMedia#detached()}, which keeps images here as they are detached.
 */
public final class DetachedMedia {

    // Folders whose names start with it are Psiche's own work, never a chapter's images.
    private static final String HIDDEN_PREFIX = ".";

    private final ChapterMedia media;
    private final Path root;

    /**
     * @param media the chapters' images, which restored images join
     * @param root  the book's {@code media/} folder
     */
    DetachedMedia(ChapterMedia media, Path root) {

        this.media = media;
        this.root = root;
    }

    /**
     * The folders of images that belong to none of {@code chapters}: detached in Psiche, or left behind by a chapter
     * renamed or deleted outside it. The most recently detached come first, then the others by name.
     */
    public List<DetachedImages> orphans(Collection<String> chapters) {

        return orphanFolders(chapters).stream()
                .map(DetachedMedia::orphan)
                .filter(group -> !group.images().isEmpty())
                .sorted(Comparator.comparing((DetachedImages group) -> group.deletedAt().orElse(LocalDateTime.MIN))
                        .reversed()
                        .thenComparing(DetachedImages::chapter))
                .toList();
    }

    /**
     * True if images are kept in a folder that belongs to none of {@code chapters}, as {@link #orphans} would list
     * them. The images are not read, only their names looked at, so it is quick enough to ask whenever the chapters
     * change.
     */
    public boolean hasOrphans(Collection<String> chapters) {

        return orphanFolders(chapters).stream().anyMatch(folder -> !ChapterMedia.imageFiles(folder).isEmpty());
    }

    /** The images of {@code chapter} as it was last deleted, if any are kept; a restored chapter offers them back. */
    public Optional<DetachedImages> latestDeleted(String chapter) {

        return folders(folder -> isDeletedFrom(folder, chapter)).stream()
                .map(DetachedMedia::orphan)
                .filter(group -> group.cause() == DetachCause.CHAPTER_DELETED && !group.images().isEmpty())
                .max(Comparator.comparing(group -> group.deletedAt().orElse(LocalDateTime.MIN)));
    }

    /**
     * Moves the images of {@code group} to {@code chapter}'s folder, and removes the group's folder once it is empty.
     * An image that meets another of its name there keeps it, or takes a number, so nothing is lost.
     */
    public void restoreTo(DetachedImages group, String chapter) {

        try {
            ChapterMedia.moveImages(group.folder(), media.folder(chapter));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot move the images of " + group.folder() + " to " + chapter, e);
        }
    }

    /** Deletes the images of {@code group} for good: they are not in Git, so they cannot come back. */
    public void deletePermanently(DetachedImages group) {

        deleteTree(group.folder());
    }

    /** The folders in {@code media/} named after none of {@code chapters}. */
    private List<Path> orphanFolders(Collection<String> chapters) {

        Set<String> names = Set.copyOf(chapters);
        return folders(folder -> !names.contains(folder.getFileName().toString()));
    }

    /** The folders directly in {@code media/} that {@code wanted} accepts, by name, but Psiche's own. */
    private List<Path> folders(Predicate<Path> wanted) {

        if (!Files.isDirectory(root))
            return List.of();

        try (Stream<Path> entries = Files.list(root)) {
            return entries.filter(Files::isDirectory)
                    .filter(folder -> !folder.getFileName().toString().startsWith(HIDDEN_PREFIX))
                    .filter(wanted)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list the folders in " + root, e);
        }
    }

    private static DetachedImages orphan(Path folder) {

        Optional<LocalDateTime> at = deletedAt(folder);
        if (at.isEmpty())
            return new DetachedImages(folder, folder.getFileName().toString(), DetachCause.CHAPTER_GONE, at,
                    ChapterMedia.imagesIn(folder));

        // Folders an image was detached to say so; any other deleted folder went with its chapter.
        DetachCause cause = cause(folder).equals(ChapterMedia.DETACHED_CAUSE) ? DetachCause.DETACHED
                : DetachCause.CHAPTER_DELETED;
        return new DetachedImages(folder, deletedChapter(folder).orElseThrow(), cause, at,
                ChapterMedia.imagesIn(folder));
    }
}
