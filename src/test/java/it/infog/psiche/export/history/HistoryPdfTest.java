package it.infog.psiche.export.history;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.export.history.model.WritingHistory;
import it.infog.psiche.git.model.BookRevision;
import it.infog.psiche.git.model.ChapterChange;
import it.infog.psiche.git.model.enums.ChapterChangeKind;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoryPdfTest {

    private static final BookProperties BOOK = new BookProperties("La strada del sale", "Romanzo",
            List.of("Anna Bianchi", "Marco Rossi"), "it-IT", "", "urn:uuid:1234");
    private static final Instant MORNING = Instant.parse("2026-03-03T09:12:00Z");
    private static final String DAWN = "chapters/01-alba.md";
    private static final String OPENING = "# Alba\n\nPartirono all'alba.\n\nNessuno parlava.\n";

    @TempDir
    Path tempDir;

    @Test
    void theCoverNamesTheBookAndTheHistoryThenTheSummaryListsEveryRevision() throws IOException {
        Path target = write(history());

        try (PDDocument pdf = Loader.loadPDF(target.toFile())) {
            String cover = page(pdf, 1);
            assertThat(cover).contains("WRITING HISTORY", "La strada del sale", "Romanzo", "Anna Bianchi, Marco Rossi",
                    "4, from 3 March 2026 to 4 March 2026", "0".repeat(37) + "004", "Psiche Writer 0.8.0 Beta",
                    "time zone Z");
            assertThat(cover).doesNotContain("Page 1 of");

            String summary = page(pdf, 2);
            assertThat(summary).contains("Summary", "First draft", "Fix a word", "Rename the opening");
            assertThat(summary).contains("La strada del sale · Writing history", "Page 2 of");
        }
    }

    @Test
    void eachRevisionShowsItsChangesAndSmallOnesOnTheSameDayAreShownTogether() throws IOException {
        Path target = write(history());

        try (PDDocument pdf = Loader.loadPDF(target.toFile())) {
            String text = new PDFTextStripper().getText(pdf);

            // Revision 1 is large, 2 and 3 a run of small ones, 4 a day later.
            assertThat(text).contains("Revision 1", "First draft", "Wrote", "Partirono all'alba.");
            assertThat(text).contains("Revisions 2–3", "2 small revisions changed",
                    "3 March 2026, 10:12–11:12");
            assertThat(text).contains("Revision 4", "02-alba (renamed from 01-alba)");
            assertThat(text).doesNotContain("Revision 2 ");

            assertThat(pdf.getDocumentCatalog().getDocumentOutline().children())
                    .extracting(PDOutlineItem::getTitle)
                    .containsExactly("Summary", "Revision 1", "Revisions 2–3", "Revision 4");
        }
    }

    @Test
    void aHistoryNeedsARevision() {
        assertThatThrownBy(() -> new WritingHistory(BOOK, List.of(), "", MORNING, ZoneOffset.UTC))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A large first draft; two small fixes an hour apart; on the next day, the chapter renamed. The texts are long
     * enough that the first draft is not a small revision.
     */
    private static WritingHistory history() {
        String draft = OPENING + "Il sale pesava sui carri.\n".repeat(30);
        String fixed = draft.replace("Nessuno parlava.", "Nessuno diceva niente.");
        String fixedAgain = fixed.replace("Partirono all'alba.", "Partirono all'alba, in silenzio.");

        List<BookRevision> revisions = new ArrayList<>();
        revisions.add(revision(1, 0, "First draft", new ChapterChange(DAWN, null, ChapterChangeKind.ADDED, "",
                draft)));
        revisions.add(revision(2, 1, "Fix a word", new ChapterChange(DAWN, null, ChapterChangeKind.CHANGED, draft,
                fixed)));
        revisions.add(revision(3, 2, "Fix another", new ChapterChange(DAWN, null, ChapterChangeKind.CHANGED, fixed,
                fixedAgain)));
        revisions.add(revision(4, 24, "Rename the opening\n\nIt is chapter two now.",
                new ChapterChange("chapters/02-alba.md", DAWN, ChapterChangeKind.RENAMED, fixedAgain, fixedAgain)));

        return new WritingHistory(BOOK, revisions, "0.8.0 Beta", MORNING.plusSeconds(86400 * 3), ZoneOffset.UTC);
    }

    private static BookRevision revision(int number, int hours, String message, ChapterChange change) {
        String id = "%040d".formatted(number);
        return new BookRevision(id, id.substring(0, 7), message, MORNING.plusSeconds(hours * 3600L), false,
                List.of(change));
    }

    private Path write(WritingHistory history) {
        Path target = tempDir.resolve("history.pdf");
        new HistoryPdf().write(history, target);
        return target;
    }

    private static String page(PDDocument pdf, int number) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(number);
        stripper.setEndPage(number);
        return stripper.getText(pdf);
    }
}
