package it.infog.psiche.media;

import it.infog.psiche.markdown.ImageLines;
import it.infog.psiche.media.model.ChapterImage;
import it.infog.psiche.media.model.MediaImage;
import it.infog.psiche.media.model.enums.ImageFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ImageUsesTest {

    private static final Path BOOK = Path.of("/books/Lorem");
    private static final Path CHAPTERS = BOOK.resolve("chapters");

    @Test
    void eachImageGetsTheParagraphsShowingItThoseShownFirstInOrderTheOthersByName() {

        List<MediaImage> images = List.of(image("anna.jpg"), image("letter.png"), image("map.png"),
                image("harbour.jpg"));
        String text = "![Map](../media/01-departure/map.png)\n\nText.\n\n![Anna](../media/01-departure/anna.jpg)\n\n"
                + "![Map again](../media/01-departure/./map.png)\n\n![Elsewhere](../media/02-crossing/letter.png)\n";

        List<ChapterImage> uses = ImageUses.of(images, ImageLines.of(text), CHAPTERS);

        assertThat(uses).extracting(use -> use.image().name())
                .containsExactly("map.png", "anna.jpg", "harbour.jpg", "letter.png");
        assertThat(uses.getFirst().uses()).hasSize(2);
        assertThat(uses.getFirst().caption()).isEqualTo("Map");
        assertThat(uses.get(2).inChapter()).isFalse();
        assertThat(uses.get(2).caption()).isEmpty();
    }

    @Test
    void anImageWhoseFileIsNotThereIsMissingButOneOnTheWebNever(@TempDir Path book) throws Exception {

        Path chapters = Files.createDirectories(book.resolve("chapters"));
        Files.createDirectories(book.resolve("media/01-departure"));
        Files.writeString(book.resolve("media/01-departure/map.png"), "a picture");
        String text = "![Map](../media/01-departure/map.png)\n\n![Gone](../media/01-departure/gone.png)\n\n"
                + "![Web](https://example.com/a.png)\n\n![Folder](../media/01-departure)\n";

        assertThat(ImageUses.missing(text, chapters)).extracting(line -> line.caption())
                .containsExactly("Gone", "Folder");
        assertThat(ImageUses.missing("No images here.", chapters)).isEmpty();
    }

    @Test
    void anImageInAFolderWithASpaceIsFoundWrittenWithTheSpaceOrWithPercentEscapes(@TempDir Path book)
            throws Exception {

        Path chapters = Files.createDirectories(book.resolve("chapters"));
        Files.createDirectories(book.resolve("media/Chapter three"));
        Files.writeString(book.resolve("media/Chapter three/map.png"), "a picture");
        String text = "![Written](<../media/Chapter three/map.png>)\n\n![Typed](../media/Chapter%20three/map.png)\n";

        assertThat(ImageUses.missing(text, chapters)).isEmpty();
    }

    private static MediaImage image(String name) {

        return new MediaImage(BOOK.resolve("media/01-departure").resolve(name), ImageFormat.PNG, 1200, 800, 1000,
                Set.of());
    }

    @Test
    void aRenamedChaptersLinksFollowItsImagesWhicheverWayTheyAreWritten() {

        String text = "![Map](../media/01-departure/map.png)\n\n![Map again](../media/01-departure/./map.png)\n\n"
                + "![Old](../media/01-departure/old/letter.png)\n\n![Other](../media/02-crossing/map.png)\n\n"
                + "![Web](https://example.org/01-departure/map.png)\n";

        String moved = ImageUses.withChapter(text, CHAPTERS, BOOK.resolve("media/01-departure"), "01 leaving");

        assertThat(ImageLines.of(moved)).extracting(line -> line.destination()).containsExactly(
                "../media/01 leaving/map.png", "../media/01 leaving/map.png", "../media/01 leaving/old/letter.png",
                "../media/02-crossing/map.png", "https://example.org/01-departure/map.png");
        assertThat(ImageLines.of(moved)).extracting(line -> line.caption())
                .containsExactly("Map", "Map again", "Old", "Other", "Web");
    }

    @Test
    void aTextWithoutImagesOfTheRenamedChapterIsLeftAsItIs() {

        String text = "Text.\n\n![Other](../media/02-crossing/map.png)\n";

        assertThat(ImageUses.withChapter(text, CHAPTERS, BOOK.resolve("media/01-departure"), "01-leaving"))
                .isSameAs(text);
    }
}
