package it.infog.psiche.git;

import it.infog.psiche.git.exceptions.GitCommandException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * Writes folders of the book as a commit left them into another folder, for an export of the book as it was then. Get
 * it through {@link BookHistory#writeFolders}. The repository and the book's files are only read.
 */
final class RevisionFiles {

    private RevisionFiles() {
    }

    /**
     * @param folders the folders, as paths in the repository
     * @param target  an empty or missing folder
     */
    static void write(Repository repo, String commitId, List<String> folders, Path target) {

        Path root = target.toAbsolutePath().normalize();
        if (!isEmptyOrMissing(root))
            throw new IllegalArgumentException(target + " is not an empty folder");

        try (RevWalk walk = new RevWalk(repo)) {
            RevCommit commit = walk.parseCommit(ObjectId.fromString(commitId));
            for (String folder : folders) {
                // Made even if the commit has no file in it, as Git keeps no empty folders.
                Files.createDirectories(root.resolve(folder));
                writeFolder(repo, commit, folder, root);
            }
        } catch (InvalidObjectIdException | MissingObjectException | IncorrectObjectTypeException e) {
            throw new GitCommandException("%s is not a commit of this book".formatted(commitId), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the book as %s left it to %s".formatted(commitId, target), e);
        }
    }

    /** Writes the files under {@code folder} in {@code commit}; links and submodules are left out. */
    private static void writeFolder(Repository repo, RevCommit commit, String folder, Path root) throws IOException {

        try (TreeWalk files = new TreeWalk(repo)) {
            files.addTree(commit.getTree());
            files.setRecursive(true);
            files.setFilter(PathFilter.create(folder));
            while (files.next()) {
                FileMode mode = files.getFileMode(0);
                if (mode != FileMode.REGULAR_FILE && mode != FileMode.EXECUTABLE_FILE)
                    continue;

                Path file = root.resolve(files.getPathString()).normalize();
                if (!file.startsWith(root))
                    throw new GitCommandException("%s would be written outside %s".formatted(files.getPathString(),
                            root));

                Files.createDirectories(file.getParent());
                try (OutputStream out = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW)) {
                    repo.open(files.getObjectId(0), Constants.OBJ_BLOB).copyTo(out);
                }
            }
        }
    }

    private static boolean isEmptyOrMissing(Path folder) {

        if (!Files.exists(folder))
            return true;
        if (!Files.isDirectory(folder))
            return false;

        try (Stream<Path> entries = Files.list(folder)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + folder, e);
        }
    }
}
