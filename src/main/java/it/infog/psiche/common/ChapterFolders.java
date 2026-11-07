package it.infog.psiche.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The folders that {@code notes/} and {@code media/} keep for a book's chapters: each chapter's own, named like its
 * file without {@code .md}, and those that keep what was deleted from it, named after it with {@value #DELETED_MARK}
 * and the time added, with {@value #DELETED_FILE} in them saying why. Such a name matches no chapter, so what they hold
 * belongs to none, as does what a folder named after a chapter renamed or deleted outside Psiche holds.
 */
public final class ChapterFolders {

    /** What a chapter's file name ends with, after the chapter's name. */
    public static final String CHAPTER_EXTENSION = ".md";
    public static final String DELETED_MARK = "-del-";
    public static final String DELETED_FILE = "deleted.properties";

    private static final DateTimeFormatter DELETED_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final Pattern DELETED_FOLDER =
            Pattern.compile("(.+)" + DELETED_MARK + "(\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2})");
    // In the deleted file: why its folder's content was deleted, in the words of the store that keeps it.
    private static final String CAUSE_KEY = "cause";

    private ChapterFolders() {
    }

    /**
     * The folder of {@code chapter} in {@code root}, which may not exist yet.
     *
     * @param what what the folder holds, as "notes", for the message of a name that cannot be a folder's
     * @throws IllegalArgumentException if {@code chapter} cannot name a folder in {@code root}
     */
    public static Path chapterFolder(Path root, String chapter, String what) {

        if (chapter.isBlank() || chapter.contains("/") || chapter.contains("\\") || chapter.equals("..")
                || chapter.equals("."))
            throw new IllegalArgumentException("'%s' cannot name a chapter's %s".formatted(chapter, what));

        return root.resolve(chapter);
    }

    // Deleted folders

    /** The folder in {@code root} that keeps what is deleted from {@code chapter} now: named after it and the time. */
    public static Path deletedFolder(Path root, String chapter, Clock clock) {

        return root.resolve(chapter + DELETED_MARK + LocalDateTime.now(clock).format(DELETED_TIME));
    }

    /** True if {@code folder} keeps something deleted from {@code chapter}, whenever it was. */
    public static boolean isDeletedFrom(Path folder, String chapter) {

        return deleted(folder).filter(matcher -> matcher.group(1).equals(chapter)).isPresent();
    }

    /** The chapter that what {@code folder} keeps was deleted from; empty if it is no deleted folder. */
    public static Optional<String> deletedChapter(Path folder) {

        return deleted(folder).map(matcher -> matcher.group(1));
    }

    /** When what {@code folder} keeps was deleted; empty if it is no deleted folder. */
    public static Optional<LocalDateTime> deletedAt(Path folder) {

        return deleted(folder).map(matcher -> LocalDateTime.parse(matcher.group(2), DELETED_TIME));
    }

    /** Says in {@code folder} that what it keeps was deleted for {@code cause}, in place of any reason it gave. */
    public static void markCause(Path folder, String cause) {

        Properties properties = new Properties();
        properties.setProperty(CAUSE_KEY, cause);
        PropertiesFiles.write(folder.resolve(DELETED_FILE), properties, "");
    }

    /** True if {@code folder} says why what it keeps was deleted. */
    public static boolean hasCause(Path folder) {

        return Files.exists(folder.resolve(DELETED_FILE));
    }

    /** Why what {@code folder} keeps was deleted, as {@link #markCause} put it; empty if it does not say. */
    public static String cause(Path folder) {

        return PropertiesFiles.readLeniently(folder.resolve(DELETED_FILE)).getProperty(CAUSE_KEY, "");
    }

    /** The parts of {@code folder}'s name, if it is a deleted folder's with a time that can be. */
    private static Optional<Matcher> deleted(Path folder) {

        Matcher matcher = DELETED_FOLDER.matcher(folder.getFileName().toString());
        if (!matcher.matches())
            return Optional.empty();

        try {
            LocalDateTime.parse(matcher.group(2), DELETED_TIME);
            return Optional.of(matcher);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
