package it.infog.psiche.git;

import it.infog.psiche.git.exceptions.GitCommandException;
import it.infog.psiche.git.model.enums.FolderState;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FS;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A folder of this computer as a book's remote, such as a USB stick or an external disk. It is either a bare
 * repository, used as it is, or any other folder, which then holds Psiche's own, {@value #BACKUPS}, where each book
 * has a bare repository named after its folder, {@code <book folder>.git}: so the drive stays free for anything else,
 * and one folder holds the backups of several books. The author may choose {@value #BACKUPS} itself too, as on a
 * second device. Hidden files, which systems leave even there, are not counted.
 */
public final class RemoteFolder {

    static final String REPOSITORY_SUFFIX = Constants.DOT_GIT;
    static final String BACKUPS = "PsicheRepository";

    private RemoteFolder() {
    }

    /** What {@code folder} holds, and so whether it can be the remote of the book at {@code book}. */
    public static FolderState state(Path folder, Path book) {

        Path chosen = folder.toAbsolutePath().normalize();
        if (!Files.isDirectory(chosen))
            return FolderState.MISSING;

        if (chosen.startsWith(book.toAbsolutePath().normalize()))
            return FolderState.INSIDE_BOOK;

        if (isGitDirectory(chosen))
            return isBareRepository(chosen) ? writable(chosen, FolderState.REPOSITORY) : FolderState.WORKING_REPOSITORY;

        if (isGitDirectory(chosen.resolve(Constants.DOT_GIT)))
            return FolderState.WORKING_REPOSITORY;

        //Psiche's folder for backups, made with the first book's repository
        Path backups = backups(chosen);
        if (!Files.exists(backups))
            return writable(chosen, FolderState.NEW_REPOSITORY);

        //nothing in it but bare repositories, maybe the book's own
        if (!Files.isDirectory(backups) || !visibleEntries(backups).stream().allMatch(RemoteFolder::isBareRepository))
            return FolderState.HAS_FILES;

        Path own = backups.resolve(repositoryName(book));
        return Files.exists(own)
                ? writable(own, FolderState.BOOK_REPOSITORY)
                : writable(backups, FolderState.NEW_REPOSITORY);
    }

    /**
     * The repository of the book at {@code book} for {@code folder}: the folder itself, if it is a bare repository,
     * or else the book's in {@value #BACKUPS}.
     */
    public static Path repository(Path folder, Path book) {

        Path chosen = folder.toAbsolutePath().normalize();
        return isBareRepository(chosen) ? chosen : backups(chosen).resolve(repositoryName(book));
    }

    /**
     * Makes the book's bare repository for {@code folder}, on branch {@code main}, with {@value #BACKUPS} if it is
     * not there yet; nothing if the repository is there already.
     *
     * @return the repository
     * @throws GitCommandException if it cannot be made
     */
    public static Path createRepository(Path folder, Path book) {

        Path repository = repository(folder, book);
        if (isBareRepository(repository))
            return repository;

        try {
            Git.init().setBare(true).setDirectory(repository.toFile()).setInitialBranch(BookRepository.DEFAULT_BRANCH)
                    .call().close();
            return repository;
        } catch (GitAPIException e) {
            throw new GitCommandException("Cannot make the book's repository in " + folder + ": " + e.getMessage(), e);
        }
    }

    /**
     * True if {@code folder} is on the same partition as the book at {@code book}, so one failing disk could take
     * both. Java cannot tell two partitions of one disk apart, so a different partition may still be the same disk.
     */
    public static boolean samePartition(Path folder, Path book) {

        try {
            return Objects.equals(Files.getFileStore(folder), Files.getFileStore(book));
        } catch (IOException e) {
            return false;
        }
    }

    /** True if {@code folder} is a repository without a working copy, as {@code git init --bare} makes them. */
    static boolean isBareRepository(Path folder) {

        if (!isGitDirectory(folder))
            return false;

        try (Repository repository = new FileRepositoryBuilder().setGitDir(folder.toFile()).build()) {
            return repository.getConfig().getBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
                    ConfigConstants.CONFIG_KEY_BARE, false);
        } catch (IOException e) {
            return false;
        }
    }

    /** True if {@code folder} is a Git repository's own folder: a bare repository, or a working copy's {@code .git}. */
    private static boolean isGitDirectory(Path folder) {

        return Files.isDirectory(folder) && RepositoryCache.FileKey.isGitRepository(folder.toFile(), FS.DETECTED);
    }

    /** Psiche's folder for backups in {@code chosen}, or {@code chosen} itself if it is that folder. */
    private static Path backups(Path chosen) {

        return BACKUPS.equals(String.valueOf(chosen.getFileName())) ? chosen : chosen.resolve(BACKUPS);
    }

    private static String repositoryName(Path book) {

        return book.toAbsolutePath().normalize().getFileName() + REPOSITORY_SUFFIX;
    }

    private static FolderState writable(Path folder, FolderState state) {

        return Files.isWritable(folder) ? state : FolderState.NOT_WRITABLE;
    }

    /** What {@code folder} holds, without the hidden files systems leave on drives. */
    private static List<Path> visibleEntries(Path folder) {

        try (Stream<Path> entries = Files.list(folder)) {
            return entries.filter(entry -> !isHidden(entry)).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the folder " + folder, e);
        }
    }

    private static boolean isHidden(Path entry) {

        try {
            return Files.isHidden(entry);
        } catch (IOException e) {
            return false;
        }
    }
}
