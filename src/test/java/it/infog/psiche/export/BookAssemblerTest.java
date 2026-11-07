package it.infog.psiche.export;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.export.model.BookExport;
import it.infog.psiche.export.model.ChapterText;
import it.infog.psiche.export.model.Contents;
import it.infog.psiche.export.model.ExportedChapter;
import it.infog.psiche.export.model.enums.EmptyChapters;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BookAssemblerTest {

    private static final BookProperties BOOK = new BookProperties("La strada del sale", "", List.of("Anna Bianchi"),
            "it-IT", "", "urn:uuid:1234");
    private static final Path CHAPTERS = Path.of("chapters");

    @Test
    void keepsTheChaptersInTheOrderGivenAndNumbersThem() {
        BookExport book = assemble(EmptyChapters.SKIP,
                new ChapterText("01-alba", "# Alba\n\nText."),
                new ChapterText("02-mezzogiorno", "# Mezzogiorno\n\nText."),
                new ChapterText("10-sera", "# Sera\n\nText."));

        assertThat(book.chapters()).extracting(ExportedChapter::id, ExportedChapter::title).containsExactly(
                org.assertj.core.groups.Tuple.tuple("chapter-1", "Alba"),
                org.assertj.core.groups.Tuple.tuple("chapter-2", "Mezzogiorno"),
                org.assertj.core.groups.Tuple.tuple("chapter-3", "Sera"));
        assertThat(book.properties()).isEqualTo(BOOK);
        assertThat(book.baseDirectory()).isEqualTo(CHAPTERS);
    }

    @Test
    void theContentsListEveryChapterUnderAHeadingInTheBooksLanguage() {
        BookExport book = assemble(EmptyChapters.SKIP,
                new ChapterText("01-alba", "# Alba"),
                new ChapterText("02-vuoto", ""),
                new ChapterText("03-sera", "Text without heading."));

        assertThat(book.contents().heading()).isEqualTo("Indice");
        assertThat(book.contents().entries()).containsExactly(
                new Contents.Entry("chapter-1", "Alba"),
                new Contents.Entry("chapter-2", "03-sera"));
        assertThat(Contents.of("pt-BR", List.of()).heading()).isEqualTo("Sumário");
        assertThat(Contents.of("ja", List.of()).heading()).isEqualTo("Contents");
    }

    @Test
    void theTitleIsTheFirstHeadingAsPlainText() {
        BookExport book = assemble(EmptyChapters.SKIP,
                new ChapterText("01-notte", "An opening line.\n\n## La *lunga* `notte`\n\n# Later heading"));

        assertThat(book.chapters().getFirst().title()).isEqualTo("La lunga notte");
    }

    @Test
    void aChapterWithoutHeadingIsKnownByItsFileName() {
        BookExport book = assemble(EmptyChapters.SKIP, new ChapterText("03-senza-titolo", "Just text."));

        assertThat(book.chapters().getFirst().title()).isEqualTo("03-senza-titolo");
    }

    @Test
    void skippedEmptyChaptersLeaveNoGapInTheIds() {
        BookExport book = assemble(EmptyChapters.SKIP,
                new ChapterText("01-alba", "# Alba"),
                new ChapterText("02-vuoto", " \n\n\t\n"),
                new ChapterText("03-sera", "# Sera"));

        assertThat(book.chapters()).extracting(ExportedChapter::id).containsExactly("chapter-1", "chapter-2");
        assertThat(book.chapters()).extracting(ExportedChapter::title).containsExactly("Alba", "Sera");
    }

    @Test
    void anEmptyChapterBecomesAPlaceholderWithItsFileNameAsHeading() {
        BookExport book = assemble(EmptyChapters.PLACEHOLDER,
                new ChapterText("01-alba", "# Alba"),
                new ChapterText("07-ritorno", ""));

        ExportedChapter placeholder = book.chapters().get(1);
        assertThat(placeholder.id()).isEqualTo("chapter-2");
        assertThat(placeholder.title()).isEqualTo("07-ritorno");
        assertThat(XhtmlRenderer.render(placeholder, LinkTargets.of(book.chapters())))
                .startsWith("<h1 id=\"chapter-2--07-ritorno\">07-ritorno<span ").endsWith("</h1>\n");
    }

    @Test
    void aChapterIsParsedOnItsOwnSoItCannotRunIntoTheNext() {
        BookExport book = assemble(EmptyChapters.SKIP,
                new ChapterText("01-codice", "```\na code block left open"),
                new ChapterText("02-due", "# Due\n\nText."));

        assertThat(book.chapters().get(1).title()).isEqualTo("Due");
        assertThat(XhtmlRenderer.render(book.chapters().get(1), LinkTargets.of(book.chapters())))
                .startsWith("<h1 id=\"chapter-2--due\">Due<span ");
    }

    @Test
    void onlyBlankTextMakesAChapterEmpty() {
        assertThat(new ChapterText("a", "").isEmpty()).isTrue();
        assertThat(new ChapterText("a", " \n\t\n").isEmpty()).isTrue();
        assertThat(new ChapterText("a", "x").isEmpty()).isFalse();
    }

    private static BookExport assemble(EmptyChapters empty, ChapterText... chapters) {
        return BookAssembler.assemble(BOOK, Optional.empty(), Optional.empty(), CHAPTERS, List.of(chapters), empty);
    }
}
