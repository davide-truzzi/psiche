package it.infog.psiche.book;

import it.infog.psiche.book.model.ProjectManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectArchiveTest {

    private static final LocalDateTime NOON = LocalDateTime.of(2026, 11, 5, 12, 0);
    private static final ProjectManifest LOREM = new ProjectManifest("lorem", "Lorem", "urn:uuid:1234", NOON);

    @TempDir
    Path tempDir;

    @Test
    void aBookComesBackWholeWithItsHistoryNotesAndEmptyFolders() throws IOException {
        Path book = sampleBook(tempDir.resolve("here/lorem"));
        Path file = tempDir.resolve("lorem-2026-11-05.psiche");
        List<Long> progress = new ArrayList<>();

        ProjectArchive.export(book, file, LOREM, (bytes, total) -> progress.add(total - bytes));
        Path imported = ProjectArchive.importTo(file, Files.createDirectories(tempDir.resolve("there")),
                (bytes, total) -> {});

        assertThat(imported).isEqualTo(tempDir.resolve("there/lorem"));
        assertThat(tree(imported)).isEqualTo(tree(book));
        assertThat(imported.resolve(".git/objects/ab/cdef"))
                .hasSameBinaryContentAs(book.resolve(".git/objects/ab/cdef"));
        assertThat(progress).last().isEqualTo(0L);
        assertThat(ProjectArchive.manifest(file)).isEqualTo(LOREM);
    }

    @Test
    void aProjectFileSavedInTheBookIsLeftOutOfIt() throws IOException {
        Path book = sampleBook(tempDir.resolve("lorem"));
        Path file = book.resolve("backup.psiche");

        ProjectArchive.export(book, file, LOREM, (bytes, total) -> {});

        try (ZipFile zip = new ZipFile(file.toFile())) {
            assertThat(zip.stream().map(ZipEntry::getName)).contains("psiche-project.properties",
                    "lorem/chapters/01-alba.md", "lorem/.git/refs/tags/").doesNotContain("lorem/backup.psiche");
        }
    }

    @Test
    void anImportReplacesTheFolderOfThatNameAndLeavesNothingElseBehind() throws IOException {
        Path file = tempDir.resolve("lorem.psiche");
        ProjectArchive.export(sampleBook(tempDir.resolve("source/lorem")), file, LOREM, (bytes, total) -> {});
        Path parent = Files.createDirectories(tempDir.resolve("books"));
        Path old = Files.createDirectories(parent.resolve("lorem/chapters"));
        Files.writeString(old.resolve("99-old.md"), "old");

        ProjectArchive.importTo(file, parent, (bytes, total) -> {});

        assertThat(parent.resolve("lorem/chapters/99-old.md")).doesNotExist();
        assertThat(parent.resolve("lorem/chapters/01-alba.md")).hasContent("# Alba\n");
        try (Stream<Path> left = Files.list(parent)) {
            assertThat(left).containsExactly(parent.resolve("lorem"));
        }
    }

    @Test
    void anEntryThatWouldLeaveTheBooksFolderIsRefusedAndNothingIsWritten() throws IOException {
        Path file = handMade("lorem", 1, "lorem/chapters/01.md", "lorem/../evil.txt");
        Path parent = Files.createDirectories(tempDir.resolve("books"));

        assertThatThrownBy(() -> ProjectArchive.importTo(file, parent, (bytes, total) -> {}))
                .isInstanceOf(UncheckedIOException.class).hasMessageContaining("outside the book's folder");
        assertThat(tempDir.resolve("evil.txt")).doesNotExist();
        try (Stream<Path> left = Files.list(parent)) {
            assertThat(left).isEmpty();
        }
    }

    @Test
    void anEntryOutsideTheBooksFolderIsRefused() throws IOException {
        Path file = handMade("lorem", 1, "other/file.txt");

        assertThatThrownBy(() -> ProjectArchive.importTo(file, tempDir, (bytes, total) -> {}))
                .hasMessageContaining("not in the book's folder");
    }

    @Test
    void aFileFromALaterPsicheOrNoProjectAtAllIsRefused() throws IOException {
        Path later = handMade("lorem", 2, "lorem/a.md");
        Path hidden = handMade("../lorem", 1, "../lorem/a.md");
        Path plainZip = tempDir.resolve("plain.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(plainZip))) {
            out.putNextEntry(new ZipEntry("a.txt"));
            out.closeEntry();
        }

        assertThatThrownBy(() -> ProjectArchive.manifest(later)).hasMessageContaining("later Psiche Writer");
        assertThatThrownBy(() -> ProjectArchive.manifest(hidden)).hasMessageContaining("damaged");
        assertThatThrownBy(() -> ProjectArchive.manifest(plainZip)).hasMessageContaining("no Psiche project");
        Path text = Files.writeString(tempDir.resolve("text.psiche"), "not a zip");
        assertThatThrownBy(() -> ProjectArchive.manifest(text)).hasMessage("text.psiche is no Psiche project");
    }

    /** A book folder with chapters, media, notes, Git's objects and an empty folder Git keeps. */
    private static Path sampleBook(Path book) throws IOException {

        Files.createDirectories(book.resolve("chapters"));
        Files.writeString(book.resolve("chapters/01-alba.md"), "# Alba\n");
        Files.createDirectories(book.resolve("media"));
        byte[] picture = new byte[3 << 20];
        new Random(7).nextBytes(picture);
        Files.write(book.resolve("media/map.png"), picture);
        Files.createDirectories(book.resolve("notes/01-alba/20261105-120000-abcd"));
        Files.writeString(book.resolve("notes/01-alba/20261105-120000-abcd/note.md"), "Marco lies.");
        Files.createDirectories(book.resolve(".git/objects/ab"));
        Files.write(book.resolve(".git/objects/ab/cdef"), new byte[] {0x78, 0x01, 0x00, (byte) 0xff});
        Files.createDirectories(book.resolve(".git/refs/tags"));
        Files.writeString(book.resolve(".gitignore"), "notes/\n");
        return book;
    }

    /** Each folder and file under {@code root}, by its path there, with the files' sizes. */
    private static List<String> tree(Path root) throws IOException {

        try (Stream<Path> paths = Files.walk(root)) {
            return paths.map(path -> root.relativize(path) + (Files.isDirectory(path) ? "/" : " " + size(path)))
                    .sorted()
                    .toList();
        }
    }

    private static long size(Path file) {

        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A project file written by hand, with a manifest of {@code format} for {@code folder} and empty {@code files}. */
    private Path handMade(String folder, int format, String... files) throws IOException {

        Path file = Files.createTempFile(tempDir, "made", ".psiche");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new ZipEntry("psiche-project.properties"));
            String manifest = "format=%d\nfolder=%s\ntitle=Lorem\nbook=\nexported=%s\n".formatted(format, folder, NOON);
            out.write(manifest.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            for (String name : files) {
                out.putNextEntry(new ZipEntry(name));
                out.write("x".getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return file;
    }
}
