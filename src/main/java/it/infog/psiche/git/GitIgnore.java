package it.infog.psiche.git;

import it.infog.psiche.git.exceptions.GitCommandException;

import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The book's {@code .gitignore}: what Git keeps out of every copy of the book, each change to it committed alone, so
 * the author's own work stays out of that commit.
 */
public final class GitIgnore {

    static final String FILE = ".gitignore";

    private final BookRepository book;
    private final Repository repository;

    GitIgnore(BookRepository book, Repository repository) {

        this.book = book;
        this.repository = repository;
    }

    /**
     * Keeps {@code pattern} out of Git in every copy of the book: adds it to the book's {@code .gitignore}, unless
     * it is there already, and commits that file alone with {@code message}.
     *
     * @param pattern a line of {@code .gitignore}, such as {@code notes/}
     * @return false if {@code .gitignore} already had it, so nothing was committed
     * @throws GitCommandException if the commit fails
     */
    public boolean add(String pattern, String message) {

        Path file = file();
        try {
            String text = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
            if (text.lines().map(String::strip).anyMatch(pattern::equals))
                return false;

            //add the line, after a line break if the file does not end with one
            String separator = text.isEmpty() || text.endsWith("\n") ? "" : "\n";
            Files.writeString(file, text + separator + pattern + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot update " + file, e);
        }

        book.commitOnly(message, List.of(file));
        return true;
    }

    /**
     * Brings into Git the files of {@code folder} that the line {@code dropped} of the book's {@code .gitignore} kept
     * out, but for those the line {@code keptOut} keeps out in its place: changes {@code .gitignore} so, and commits it
     * alone with the files of {@code folder} that Git now sees, with {@code message}.
     *
     * @param folder a folder of the book
     * @return false if {@code .gitignore} already had {@code keptOut} and not {@code dropped}, so nothing was committed
     * @throws GitCommandException if the commit fails
     */
    public boolean track(String dropped, String keptOut, Path folder, String message) {

        Path file = file();
        try {
            List<String> lines = Files.exists(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : List.of();
            List<String> kept = new ArrayList<>(lines.stream().filter(line -> !line.strip().equals(dropped)).toList());
            boolean hasKeptOut = kept.stream().map(String::strip).anyMatch(keptOut::equals);
            if (hasKeptOut && kept.size() == lines.size())
                return false;

            if (!hasKeptOut)
                kept.add(keptOut);
            Files.writeString(file, String.join("\n", kept) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot update " + file, e);
        }

        //the folder's files Git sees now, beside .gitignore
        String prefix = BookRepository.repositoryPath(repository, folder) + "/";
        Path workTree = repository.getWorkTree().toPath();
        List<Path> files = new ArrayList<>(List.of(file));
        book.status().getUntracked().stream().filter(path -> path.startsWith(prefix)).map(workTree::resolve)
                .forEach(files::add);
        book.commitOnly(message, files);
        return true;
    }

    /** True if the book's {@code .gitignore} has {@code pattern} as one of its lines; false if it cannot be read. */
    public boolean has(String pattern) {

        try {
            Path file = file();
            return Files.exists(file) && Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .map(String::strip)
                    .anyMatch(pattern::equals);
        } catch (IOException e) {
            return false;
        }
    }

    private Path file() {

        return repository.getWorkTree().toPath().resolve(FILE);
    }
}
