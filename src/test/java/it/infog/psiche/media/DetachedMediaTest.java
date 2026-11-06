package it.infog.psiche.media;

import it.infog.psiche.media.model.DetachedGroup;
import it.infog.psiche.media.model.MediaImage;
import it.infog.psiche.media.model.enums.DetachCause;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DetachedMediaTest {

    private static final Instant MORNING = ChapterMediaTest.MORNING;

    @TempDir
    Path book;

    private Path media;
    private Path pictures;

    @BeforeEach
    void setUp() throws Exception {

        media = Files.createDirectories(book.resolve("media"));
        pictures = Files.createDirectories(book.resolve("pictures"));
    }

    @Test
    void imagesTellHowTheyLeftTheirChapterTheMostRecentFirst() throws Exception {

        store(MORNING).add("03-night", image("moon.png"));
        Path map = store(MORNING).add("01-dawn", image("map.png"));
        store(MORNING).detach("01-dawn", map);
        store(MORNING).add("02-dusk", image("sun.png"));
        store(MORNING.plus(Duration.ofHours(2))).binChapter("02-dusk");

        List<DetachedGroup> orphans = store(MORNING).detached().orphans(List.of("04-day"));

        assertThat(orphans).extracting(DetachedGroup::chapter).containsExactly("02-dusk", "01-dawn", "03-night");
        assertThat(orphans).extracting(DetachedGroup::cause).containsExactly(DetachCause.CHAPTER_DELETED,
                DetachCause.DETACHED, DetachCause.CHAPTER_GONE);
        assertThat(orphans.get(1).deletedAt()).contains(LocalDateTime.of(2026, 11, 6, 9, 30));
        assertThat(orphans.get(2).deletedAt()).isEmpty();
        assertThat(orphans.getFirst().images()).extracting(MediaImage::name).containsExactly("sun.png");
    }

    @Test
    void aChaptersOwnImagesAreNoOrphans() throws Exception {

        store(MORNING).add("01-dawn", image("map.png"));

        assertThat(store(MORNING).detached().orphans(List.of("01-dawn"))).isEmpty();
        assertThat(store(MORNING).detached().hasOrphans(List.of("01-dawn"))).isFalse();
        assertThat(store(MORNING).detached().hasOrphans(List.of())).isTrue();
    }

    @Test
    void aRestoredChapterFindsTheImagesItWasLastDeletedWith() throws Exception {

        ChapterMedia store = store(MORNING);
        store.add("01-dawn", image("map.png"));
        store.binChapter("01-dawn");
        store(MORNING.plus(Duration.ofHours(1))).detach("01-dawn", store.add("01-dawn", image("other.png")));

        assertThat(store.detached().latestDeleted("01-dawn")).hasValueSatisfying(group ->
                assertThat(group.images()).extracting(MediaImage::name).containsExactly("map.png"));
        assertThat(store.detached().latestDeleted("01-da")).isEmpty();
    }

    @Test
    void restoredImagesJoinTheChapterAndTheirFolderGoes() throws Exception {

        ChapterMedia store = store(MORNING);
        store.add("01-dawn", image("map.png"));
        store.binChapter("01-dawn");
        store.add("02-dusk", image("other/map.png", 900));
        DetachedGroup group = store.detached().orphans(List.of("02-dusk")).getFirst();

        store.detached().restoreTo(group, "02-dusk");

        assertThat(group.folder()).doesNotExist();
        assertThat(store.images("02-dusk")).extracting(MediaImage::name).containsExactly("map-2.png", "map.png");
    }

    @Test
    void imagesDeletedPermanentlyAreGone() throws Exception {

        ChapterMedia store = store(MORNING);
        store.add("01-dawn", image("map.png"));
        store.binChapter("01-dawn");
        DetachedGroup group = store.detached().orphans(List.of()).getFirst();

        store.detached().deletePermanently(group);

        assertThat(group.folder()).doesNotExist();
        assertThat(store.detached().orphans(List.of())).isEmpty();
    }

    private ChapterMedia store(Instant now) {

        return new ChapterMedia(media, Clock.fixed(now, ZoneOffset.UTC));
    }

    private Path image(String name) throws Exception {

        return image(name, 1200);
    }

    private Path image(String name, int width) throws Exception {

        Path file = pictures.resolve(name);
        Files.createDirectories(file.getParent());
        return TestImages.png(file, width, 800);
    }
}
