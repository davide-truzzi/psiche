package it.infog.psiche.media;

import it.infog.psiche.media.model.MediaImage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChapterMediaTest {

    @TempDir
    Path book;

    static final Instant MORNING = Instant.parse("2026-11-06T09:30:00Z");

    private Path media;
    private Path pictures;
    private ChapterMedia store;

    @BeforeEach
    void setUp() throws Exception {

        media = Files.createDirectories(book.resolve("media"));
        pictures = Files.createDirectories(book.resolve("pictures"));
        store = new ChapterMedia(media, Clock.fixed(MORNING, ZoneOffset.UTC));
    }

    @Test
    void anImageIsCopiedIntoTheChaptersOwnFolderUnderItsName() throws Exception {

        Path source = TestImages.png(pictures.resolve("map.png"), 1200, 800);

        Path added = store.add("01-departure", source);

        assertThat(added).isEqualTo(media.resolve("01-departure").resolve("map.png"));
        assertThat(Files.mismatch(source, added)).isEqualTo(-1);
        assertThat(source).exists();
    }

    @Test
    void theSameImageAddedAgainIsUsedAsItIs() throws Exception {

        Path source = TestImages.png(pictures.resolve("map.png"), 1200, 800);
        Path first = store.add("01-departure", source);

        assertThat(store.add("01-departure", source)).isEqualTo(first);
        assertThat(store.add("01-departure", first)).isEqualTo(first);
        assertThat(media.resolve("01-departure")).isDirectoryContaining(path -> true)
                .satisfies(folder -> assertThat(Files.list(folder).count()).isEqualTo(1));
    }

    @Test
    void anotherImageOfTheSameNameTakesANumberAndNothingIsOverwritten() throws Exception {

        Path old = store.add("01-departure", TestImages.png(pictures.resolve("map.png"), 1200, 800));
        byte[] kept = Files.readAllBytes(old);
        Path other = TestImages.png(Files.createDirectories(pictures.resolve("new")).resolve("map.png"), 1000, 700);

        Path added = store.add("01-departure", other);

        assertThat(added.getFileName()).hasToString("map-2.png");
        assertThat(Files.readAllBytes(old)).isEqualTo(kept);
    }

    @Test
    void onlyJpegAndPngFilesCanBeAdded() throws Exception {

        Path gif = TestImages.write(pictures.resolve("anim.gif"), 10, 10, "gif");

        assertThatThrownBy(() -> store.add("01-departure", gif)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("anim.gif is not a JPEG or PNG image");
        assertThat(media.resolve("01-departure")).doesNotExist();
    }

    @Test
    void aChaptersImagesAreListedByNameLeavingOutWhatIsNoImage() throws Exception {

        store.add("01-departure", TestImages.png(pictures.resolve("map.png"), 1200, 800));
        store.add("01-departure", TestImages.jpeg(pictures.resolve("anna.jpg"), 1200, 1800, 0.9f));
        Path folder = media.resolve("01-departure");
        Files.writeString(folder.resolve("broken.png"), "not an image");
        Files.writeString(folder.resolve("readme.txt"), "a note");
        Files.writeString(folder.resolve(".map.png.tmp"), "half a copy");

        assertThat(store.images("01-departure")).extracting(MediaImage::name).containsExactly("anna.jpg", "map.png");
        assertThat(store.images("02-the-crossing")).isEmpty();
    }

    @Test
    void theBooksOwnFilesInMediaAreNoChaptersImages() throws Exception {

        TestImages.png(media.resolve("cover.png"), 1600, 2400);

        assertThat(store.images("cover.png")).isEmpty();
    }

    @Test
    void aNameThatCouldLeaveMediaNamesNoChapter() {

        for (String name : new String[]{"", "..", ".", "a/b", "a\\b"})
            assertThatThrownBy(() -> store.folder(name)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRenamedChaptersImagesMoveToItsNewName() throws Exception {

        store.add("01-departure", TestImages.png(pictures.resolve("map.png"), 1200, 800));

        store.rename("01-departure", "01-leaving");

        assertThat(media.resolve("01-departure")).doesNotExist();
        assertThat(store.images("01-leaving")).extracting(MediaImage::name).containsExactly("map.png");
    }

    @Test
    void aRenamedChaptersImagesJoinThoseOfItsNewNameAndNothingIsOverwritten() throws Exception {

        store.add("01-departure", TestImages.png(pictures.resolve("map.png"), 1200, 800));
        Path other = Files.createDirectories(pictures.resolve("other"));
        store.add("01-leaving", TestImages.png(other.resolve("map.png"), 900, 900));

        store.rename("01-departure", "01-leaving");

        assertThat(media.resolve("01-departure")).doesNotExist();
        assertThat(store.images("01-leaving")).extracting(MediaImage::name).containsExactly("map-2.png", "map.png");
    }

    @Test
    void aDeletedChaptersImagesAreKeptUnderItsNameAndTheTime() throws Exception {

        store.add("01-departure", TestImages.png(pictures.resolve("map.png"), 1200, 800));

        store.binChapter("01-departure");

        assertThat(store.images("01-departure")).isEmpty();
        assertThat(media.resolve("01-departure-del-2026-11-06-09-30-00").resolve("map.png")).exists();
    }

    @Test
    void aDetachedImageLeavesItsChapterAndTheEmptyFolderGoes() throws Exception {

        Path map = store.add("01-departure", TestImages.png(pictures.resolve("map.png"), 1200, 800));

        store.detach("01-departure", map);

        assertThat(media.resolve("01-departure")).doesNotExist();
        assertThat(media.resolve("01-departure-del-2026-11-06-09-30-00").resolve("map.png")).exists();
    }

    @Test
    void psicheOwnFilesInMediaAreNeverAChaptersImages() throws Exception {

        Files.writeString(media.resolve("book.properties"), "title=Lorem");
        Files.createDirectories(media.resolve(".import"));

        assertThat(store.detached().orphans(List.of())).isEmpty();
        assertThat(store.detached().hasOrphans(List.of())).isFalse();
    }
}
