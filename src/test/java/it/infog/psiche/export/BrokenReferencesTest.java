package it.infog.psiche.export;

import it.infog.psiche.export.model.BrokenReference;
import it.infog.psiche.export.model.ChapterText;
import it.infog.psiche.export.model.enums.ExportFormat;
import it.infog.psiche.export.model.enums.ReferenceProblem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BrokenReferencesTest {

    @TempDir
    Path chapters;

    @BeforeEach
    void images() throws IOException {

        BufferedImage pixel = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Files.createDirectories(chapters.resolve("media"));
        ImageIO.write(pixel, "png", chapters.resolve("media/map.png").toFile());
        ImageIO.write(pixel, "bmp", chapters.resolve("media/old.bmp").toFile());
        Files.writeString(chapters.resolve("media/notes.txt"), "not an image");
    }

    @Test
    void imagesMissingOrNotImagesAreFoundWithTheLineTheyAreOn() {

        ChapterText alba = new ChapterText("01-alba", """
                # Alba

                ![Map](media/map.png)

                ![Gone](media/gone.png)

                ![Notes](media/notes.txt)
                """);

        assertThat(BrokenReferences.in(List.of(alba), List.of(alba), chapters, ExportFormat.PDF)).containsExactly(
                new BrokenReference("01-alba", 5, ReferenceProblem.MISSING_IMAGE, "media/gone.png"),
                new BrokenReference("01-alba", 7, ReferenceProblem.UNUSABLE_IMAGE, "media/notes.txt"));
    }

    @Test
    void anImageADocumentShowsCanBeOneAnEpubCannot() {

        ChapterText alba = new ChapterText("01-alba", "![Old](media/old.bmp)\n");

        assertThat(BrokenReferences.in(List.of(alba), List.of(alba), chapters, ExportFormat.DOCX)).isEmpty();
        assertThat(BrokenReferences.in(List.of(alba), List.of(alba), chapters, ExportFormat.EPUB)).containsExactly(
                new BrokenReference("01-alba", 1, ReferenceProblem.UNUSABLE_IMAGE, "media/old.bmp"));
    }

    @Test
    void linksToChaptersOrHeadingsOfTheBookAreFineAndOthersLeadNowhere() {

        ChapterText alba = new ChapterText("01-alba", """
                # Alba

                See [the storm](02-sera.md#the-storm), [the calm](02-sera.md#the-calm), [dawn](#alba),
                [the end](03-gone.md) and [the web](https://example.org).
                """);
        ChapterText sera = new ChapterText("02-sera", "# Sera\n\n## The storm\n");

        assertThat(BrokenReferences.in(List.of(alba), List.of(alba, sera), chapters, ExportFormat.ODT))
                .containsExactly(
                        new BrokenReference("01-alba", 3, ReferenceProblem.BROKEN_LINK, "02-sera.md#the-calm"),
                        new BrokenReference("01-alba", 4, ReferenceProblem.BROKEN_LINK, "03-gone.md"));
    }

    @Test
    void onlyTheChaptersCheckedAreLookedAtInTheOrderGiven() {

        ChapterText alba = new ChapterText("01-alba", "[next](03-gone.md)\n");
        ChapterText sera = new ChapterText("02-sera", "![Gone](media/gone.png)\n");

        assertThat(BrokenReferences.in(List.of(sera), List.of(alba, sera), chapters, ExportFormat.PDF))
                .extracting(BrokenReference::chapter)
                .containsExactly("02-sera");
        assertThat(BrokenReferences.in(List.of(alba, sera), List.of(alba, sera), chapters, ExportFormat.PDF))
                .extracting(BrokenReference::chapter)
                .containsExactly("01-alba", "02-sera");
    }

    @Test
    void anImageAtAWebAddressIsNeverFetched() {

        ChapterText alba = new ChapterText("01-alba", "![Far](https://example.org/far.png)\n");

        assertThat(BrokenReferences.in(List.of(alba), List.of(alba), chapters, ExportFormat.EPUB)).isEmpty();
    }
}
