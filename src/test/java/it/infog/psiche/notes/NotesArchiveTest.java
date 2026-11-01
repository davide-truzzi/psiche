package it.infog.psiche.notes;

import it.infog.psiche.notes.model.BoardView;
import it.infog.psiche.notes.model.ImportReport;
import it.infog.psiche.notes.model.Note;
import it.infog.psiche.notes.model.NoteLayout;
import it.infog.psiche.notes.model.NotesManifest;
import it.infog.psiche.notes.model.OrphanGroup;
import it.infog.psiche.notes.model.enums.NoteColor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** Two computers, a laptop and a desktop, each with the book's notes, passing them to each other as exports. */
class NotesArchiveTest {

    private static final LocalDateTime MORNING = LocalDateTime.of(2026, 11, 1, 9, 30, 0);
    private static final NotesManifest MANIFEST = new NotesManifest("urn:uuid:5f1c3e9a-7d2b-4c1e-9a3f-2b8d6e4f1a07",
            "La strada del sale");
    private static final NoteLayout LAYOUT = new NoteLayout(40, 60, 220, 160, 1, NoteColor.PAPER);
    private static final NoteStore.CopyProgress NONE = (copied, total) -> {};

    @TempDir
    Path tempDir;

    // Numbers the zips a test makes.
    private int exports;

    @Test
    void anExportBringsEveryNoteWithItsFileBoardAndDeletedNotesToABookWithout() throws IOException {
        NoteStore laptop = laptop();
        Note note = laptop.create("01-alba", LAYOUT);
        laptop.saveText("01-alba", note.id(), "Marco lies about the letter.");
        laptop.attach("01-alba", note.id(), file("castello.jpg", 300_000), NONE);
        laptop.saveBoardView("01-alba", new BoardView(0.8, 120, 40));
        Note binned = laptop.create("02-sera", layout(200, 300));
        laptop.saveText("02-sera", binned.id(), "Lucia waits by the gate.");
        laptop.binNote("02-sera", binned.id());

        ImportReport report = importInto(desktop(), export(laptop));

        NoteStore desktop = desktop();
        assertThat(report).isEqualTo(new ImportReport(2, 0, 0));
        Note copied = desktop.notes("01-alba").getFirst();
        assertThat(copied.id()).isEqualTo(note.id());
        assertThat(copied.text()).isEqualTo("Marco lies about the letter.");
        assertThat(copied.layout()).isEqualTo(LAYOUT);
        assertThat(copied.attachment().orElseThrow()).hasFileName("castello.jpg")
                .hasSameBinaryContentAs(tempDir.resolve("castello.jpg"));
        assertThat(desktop.boardView("01-alba")).isEqualTo(new BoardView(0.8, 120, 40));
        assertThat(desktop.orphans(List.of("01-alba", "02-sera")))
                .flatExtracting(OrphanGroup::notes).extracting(Note::text)
                .containsExactly("Lucia waits by the gate.");
    }

    @Test
    void textIsCompressedAndOtherFilesAreStoredAsTheyAre() throws IOException {
        NoteStore laptop = laptop();
        Note note = laptop.create("01-alba", LAYOUT);
        laptop.saveText("01-alba", note.id(), "Salt, salt, salt. ".repeat(500));
        laptop.attach("01-alba", note.id(), file("saline.mp4", 200_000), NONE);

        try (ZipFile zip = new ZipFile(export(laptop).toFile())) {
            ZipEntry text = entry(zip, "note.md");
            ZipEntry video = entry(zip, "saline.mp4");
            assertThat(text.getCompressedSize()).isLessThan(text.getSize() / 10);
            assertThat(video.getCompressedSize()).isGreaterThanOrEqualTo(video.getSize());
        }
    }

    @Test
    void theManifestTellsWhoseNotesTheZipHolds() {
        NoteStore laptop = laptop();
        laptop.create("01-alba", LAYOUT);
        NotesManifest accented = new NotesManifest("urn:uuid:1", "Città di sale");

        Path zip = tempDir.resolve("notes.zip");
        new NotesArchive(laptop).export(zip, accented, NONE);

        assertThat(NotesArchive.manifest(zip)).isEqualTo(accented);
    }

    @Test
    void aNoteChangedOnlyInTheZipIsUpdatedHere() {
        NoteStore laptop = laptop();
        Note note = laptop.create("01-alba", LAYOUT);
        laptop.saveText("01-alba", note.id(), "First thought.");
        importInto(desktop(), export(laptop));

        laptop.saveText("01-alba", note.id(), "Second thought, on the train.");
        ImportReport report = importInto(desktop(), export(laptop));

        assertThat(report).isEqualTo(new ImportReport(0, 1, 0));
        assertThat(desktop().notes("01-alba")).extracting(Note::text).containsExactly("Second thought, on the train.");
    }

    @Test
    void aNoteChangedOnlyHereIsKept() {
        NoteStore laptop = laptop();
        Note note = laptop.create("01-alba", LAYOUT);
        laptop.saveText("01-alba", note.id(), "First thought.");
        importInto(desktop(), export(laptop));

        desktop().saveText("01-alba", note.id(), "Rewritten at the desk.");
        ImportReport report = importInto(desktop(), export(laptop));

        assertThat(report.isEmpty()).isTrue();
        assertThat(desktop().notes("01-alba")).extracting(Note::text).containsExactly("Rewritten at the desk.");
    }

    @Test
    void aNoteChangedOnBothSidesIsKeptTwiceAndTheSameZipNeverAddsItAgain() {
        NoteStore laptop = laptop();
        Note note = laptop.create("01-alba", LAYOUT);
        laptop.saveText("01-alba", note.id(), "First thought.");
        importInto(desktop(), export(laptop));
        desktop().saveText("01-alba", note.id(), "Rewritten at the desk.");
        laptop.saveText("01-alba", note.id(), "Rewritten on the train.");

        Path zip = export(laptop);
        ImportReport report = importInto(desktop(), zip);
        ImportReport again = importInto(desktop(), zip);

        assertThat(report).isEqualTo(new ImportReport(0, 0, 1));
        assertThat(again.isEmpty()).isTrue();
        List<Note> notes = desktop().notes("01-alba");
        assertThat(notes).extracting(Note::text, Note::duplicateOnImport)
                .containsExactly(tuple("Rewritten at the desk.", false),
                        tuple("Rewritten on the train.", true));
        NoteLayout beside = notes.get(1).layout();
        assertThat(beside.x()).isEqualTo(LAYOUT.x() + LAYOUT.width() + 16);
        assertThat(beside.y()).isEqualTo(LAYOUT.y());
        assertThat(beside.order()).isGreaterThan(notes.getFirst().layout().order());
    }

    @Test
    void aDuplicateNearTheBoardsRightEdgeGoesToTheLeftOfItsOriginal() {
        NoteLayout nearEdge = new NoteLayout(1300, 500, 220, 160, 1, NoteColor.SKY);
        NoteStore laptop = laptop();
        Note note = laptop.create("01-alba", nearEdge);
        importInto(desktop(), export(laptop));
        desktop().saveText("01-alba", note.id(), "Here.");
        laptop.saveText("01-alba", note.id(), "There.");

        importInto(desktop(), export(laptop));

        assertThat(desktop().notes("01-alba")).filteredOn(Note::duplicateOnImport)
                .extracting(duplicate -> duplicate.layout().x()).containsExactly(1300.0 - 220 - 16);
    }

    @Test
    void aPlaceAndColourChangedOnlyInTheZipAreTakenButNotItsPlaceInTheStack() {
        NoteStore laptop = laptop();
        Note note = laptop.create("01-alba", LAYOUT);
        importInto(desktop(), export(laptop));
        desktop().saveLayout("01-alba", note.id(), layoutOrdered(LAYOUT, 9));

        NoteLayout moved = new NoteLayout(600, 900, 300, 240, 4, NoteColor.ROSE);
        laptop.saveLayout("01-alba", note.id(), moved);
        ImportReport report = importInto(desktop(), export(laptop));

        assertThat(report).isEqualTo(new ImportReport(0, 1, 0));
        assertThat(desktop().notes("01-alba")).extracting(Note::layout)
                .containsExactly(new NoteLayout(600, 900, 300, 240, 9, NoteColor.ROSE));
    }

    @Test
    void aNoteMovedOnBothSidesKeepsThisComputersPlaceAndIsNeverDuplicated() {
        NoteStore laptop = laptop();
        Note note = laptop.create("01-alba", LAYOUT);
        importInto(desktop(), export(laptop));
        NoteLayout here = new NoteLayout(100, 100, 220, 160, 1, NoteColor.SAGE);
        desktop().saveLayout("01-alba", note.id(), here);
        laptop.saveLayout("01-alba", note.id(), new NoteLayout(900, 900, 220, 160, 1, NoteColor.ROSE));

        ImportReport report = importInto(desktop(), export(laptop));

        assertThat(report.isEmpty()).isTrue();
        assertThat(desktop().notes("01-alba")).extracting(Note::layout).containsExactly(here);
    }

    @Test
    void notesAddedOnEachSideAreAllKept() {
        NoteStore laptop = laptop();
        Note shared = laptop.create("01-alba", LAYOUT);
        importInto(desktop(), export(laptop));
        Note onLaptop = laptop.create("01-alba", layout(400, 60));
        Note onDesktop = desktop().create("01-alba", layout(700, 60));

        ImportReport report = importInto(desktop(), export(laptop));

        assertThat(report).isEqualTo(new ImportReport(1, 0, 0));
        assertThat(desktop().notes("01-alba")).extracting(Note::id)
                .containsExactlyInAnyOrder(shared.id(), onLaptop.id(), onDesktop.id());
    }

    @Test
    void aNoteKeepsTheFolderItHasHereSoADeletionIsNeverCarriedOver() {
        NoteStore laptop = laptop();
        Note binnedHere = laptop.create("01-alba", LAYOUT);
        Note binnedThere = laptop.create("01-alba", layout(400, 60));
        importInto(desktop(), export(laptop));
        desktop().binNote("01-alba", binnedHere.id());
        laptop.binNote("01-alba", binnedThere.id());

        ImportReport report = importInto(desktop(), export(laptop));

        assertThat(report.isEmpty()).isTrue();
        assertThat(desktop().notes("01-alba")).extracting(Note::id).containsExactly(binnedThere.id());
        assertThat(desktop().orphans(List.of("01-alba"))).flatExtracting(OrphanGroup::notes).extracting(Note::id)
                .containsExactly(binnedHere.id());
    }

    @Test
    void aFileChangedOnlyInTheZipReplacesTheOneHere() throws IOException {
        NoteStore laptop = laptop();
        Note note = laptop.create("01-alba", LAYOUT);
        laptop.attach("01-alba", note.id(), file("bozza.pdf", 1000), NONE);
        importInto(desktop(), export(laptop));

        laptop.attach("01-alba", note.id(), file("mappa.pdf", 2000), NONE);
        importInto(desktop(), export(laptop));

        Path folder = desktopNotes().resolve("01-alba").resolve(note.id());
        assertThat(desktop().notes("01-alba").getFirst().attachment()).contains(folder.resolve("mappa.pdf"));
        assertThat(folder.resolve("mappa.pdf")).hasSameBinaryContentAs(tempDir.resolve("mappa.pdf"));
        assertThat(folder.resolve("bozza.pdf")).doesNotExist();
    }

    @Test
    void anExportRemembersWhatItHoldsButABackupDoesNot() throws IOException {
        NoteStore laptop = laptop();
        Note note = laptop.create("01-alba", LAYOUT);
        Path properties = laptopNotes().resolve("01-alba").resolve(note.id()).resolve(NoteFolders.PROPERTIES_FILE);

        new NotesArchive(laptop).backup(tempDir.resolve("backup.zip"), MANIFEST, NONE);
        String afterBackup = Files.readString(properties);
        export(laptop);

        assertThat(afterBackup).doesNotContain(NotePrints.CONTENT_KEY);
        assertThat(Files.readString(properties)).contains(NotePrints.CONTENT_KEY, NotePrints.LAYOUT_KEY);
    }

    @Test
    void aZipWithAPathLeavingTheNotesIsRefusedBeforeAnythingChanges() throws IOException {
        Path zip = zip(Map.of(
                NotesArchive.MANIFEST, "format=1\nbook=x\n",
                "notes/01-alba/20261101-093000-ab12/note.md", "A note.",
                "notes/01-alba/../../escaped.txt", "Out."));

        assertThatThrownBy(() -> importInto(desktop(), zip)).isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("escaped.txt");
        assertThat(desktopNotes()).doesNotExist();
        assertThat(tempDir.resolve("escaped.txt")).doesNotExist();
    }

    @Test
    void aZipThatIsNoExportOfNotesIsRefused() throws IOException {
        Path other = zip(Map.of("chapters/01-alba.md", "# Alba"));
        Path later = zip(Map.of(NotesArchive.MANIFEST, "format=2\n"));

        assertThatThrownBy(() -> NotesArchive.manifest(other)).hasMessageContaining("no export of Psiche notes");
        assertThatThrownBy(() -> importInto(desktop(), later)).hasMessageContaining("later Psiche Writer");
        assertThat(desktopNotes()).doesNotExist();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "PSICHE_BIG_TESTS", matches = "true")
    void aFileOverFourGigabytesGoesThroughAnExport() throws IOException {
        NoteStore laptop = laptop();
        Note note = laptop.create("01-alba", LAYOUT);
        Path big = laptopNotes().resolve("01-alba").resolve(note.id()).resolve("film.mov");
        long size = 4L * 1024 * 1024 * 1024 + 4096;
        try (RandomAccessFile sparse = new RandomAccessFile(big.toFile(), "rw")) {
            sparse.setLength(size);
        }

        importInto(desktop(), export(laptop));

        assertThat(desktop().notes("01-alba").getFirst().attachment().orElseThrow()).hasFileName("film.mov")
                .satisfies(file -> assertThat(Files.size(file)).isEqualTo(size));
    }

    // Helpers

    private NoteStore laptop() {

        return new NoteStore(laptopNotes(), Clock.fixed(MORNING.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    private NoteStore desktop() {

        return new NoteStore(desktopNotes(), Clock.fixed(MORNING.plusHours(5).toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC));
    }

    private Path laptopNotes() {

        return tempDir.resolve("laptop/notes");
    }

    private Path desktopNotes() {

        return tempDir.resolve("desktop/notes");
    }

    private Path export(NoteStore store) {

        Path zip = tempDir.resolve("export-" + ++exports + ".zip");
        new NotesArchive(store).export(zip, MANIFEST, NONE);
        return zip;
    }

    private static ImportReport importInto(NoteStore store, Path zip) {

        return new NotesArchive(store).importFrom(zip, NONE);
    }

    /** A file of {@code size} random bytes in the test's folder, as an author's file to attach. */
    private Path file(String name, int size) throws IOException {

        byte[] bytes = new byte[size];
        new Random(size).nextBytes(bytes);
        return Files.write(tempDir.resolve(name), bytes);
    }

    private Path zip(Map<String, String> entries) throws IOException {

        Path zip = tempDir.resolve("made-" + ++exports + ".zip");
        try (OutputStream file = Files.newOutputStream(zip); ZipOutputStream out = new ZipOutputStream(file)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return zip;
    }

    private static ZipEntry entry(ZipFile zip, String fileName) {

        return zip.stream().filter(entry -> entry.getName().endsWith("/" + fileName)).findFirst().orElseThrow();
    }

    private static NoteLayout layout(double x, double y) {

        return new NoteLayout(x, y, 220, 160, 1, NoteColor.PAPER);
    }

    private static NoteLayout layoutOrdered(NoteLayout layout, int order) {

        return new NoteLayout(layout.x(), layout.y(), layout.width(), layout.height(), order, layout.color());
    }
}
