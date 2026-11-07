package it.infog.psiche.export;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.export.model.BookExport;
import it.infog.psiche.export.model.ChapterText;
import it.infog.psiche.export.model.ExportedChapter;
import it.infog.psiche.export.model.LinkPlace;
import it.infog.psiche.export.model.enums.EmptyChapters;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LinkTargetsTest {

    private static final BookProperties BOOK = new BookProperties("Alba", "", List.of(), "it", "", "");

    private final BookExport book = BookAssembler.assemble(BOOK, Optional.empty(), Optional.empty(),
            Path.of("chapters"), List.of(
                    new ChapterText("01-alba", "# Alba\n\n## Night\n\n## Night\n"),
                    new ChapterText("02-vuoto", ""),
                    new ChapterText("03 La sera", "# Sera\n\n## The storm\n")), EmptyChapters.SKIP);
    private final LinkTargets links = LinkTargets.of(book.chapters());
    private final ExportedChapter alba = book.chapters().getFirst();

    @Test
    void aLinkToAChapterOfTheBookLeadsToItsStartAndOneToAHeadingToTheHeading() {

        assertThat(links.place(alba, "03%20La%20sera.md")).hasValue(new LinkPlace("chapter-2", "chapter-2"));
        assertThat(links.place(alba, "03 La sera.md#the-storm"))
                .hasValue(new LinkPlace("chapter-2", "chapter-2--the-storm"));
        assertThat(links.place(alba, "#night-1")).hasValue(new LinkPlace("chapter-1", "chapter-1--night-1"));
    }

    @Test
    void aLinkToAChapterLeftOutAHeadingNotThereOrAWebAddressLeadsNowhereInTheBook() {

        assertThat(links.place(alba, "02-vuoto.md")).isEmpty();
        assertThat(links.place(alba, "03 La sera.md#the-calm")).isEmpty();
        assertThat(links.place(alba, "04-gone.md")).isEmpty();
        assertThat(links.place(alba, "https://example.org")).isEmpty();
        assertThat(LinkTargets.isWeb("https://example.org")).isTrue();
        assertThat(LinkTargets.isWeb("ftp://example.org")).isFalse();
    }

    @Test
    void aChapterExportedAloneLeadsOnlyToItsOwnHeadings() {

        ExportedChapter chapter = BookAssembler.alone("01-alba", "# Alba\n\n## Night\n");
        LinkTargets alone = LinkTargets.alone(chapter);

        assertThat(alone.place(chapter, "#night")).hasValue(new LinkPlace("chapter", "chapter--night"));
        assertThat(alone.place(chapter, "01-alba.md#night")).hasValue(new LinkPlace("chapter", "chapter--night"));
        assertThat(alone.place(chapter, "01-alba.md")).isEmpty();
        assertThat(alone.place(chapter, "02-due.md")).isEmpty();
    }
}
