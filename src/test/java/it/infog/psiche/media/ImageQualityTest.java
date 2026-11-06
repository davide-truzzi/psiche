package it.infog.psiche.media;

import it.infog.psiche.media.model.MediaImage;
import it.infog.psiche.media.model.enums.ImageFormat;
import it.infog.psiche.media.model.enums.QualityIssue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageQualityTest {

    @TempDir
    Path folder;

    @Test
    void aLargeImageSavedAtAGoodQualityIsFine() throws Exception {

        MediaImage image = ImageFiles.read(TestImages.jpeg(folder.resolve("good.jpg"), 1800, 1200, 0.9f));

        assertThat(image.format()).isEqualTo(ImageFormat.JPEG);
        assertThat(image.width()).isEqualTo(1800);
        assertThat(image.height()).isEqualTo(1200);
        assertThat(image.bytes()).isEqualTo(Files.size(image.file()));
        assertThat(image.issues()).isEmpty();
    }

    @Test
    void anImageUnder900PixelsOnItsLongerSideIsTooSmall() throws Exception {

        assertThat(ImageFiles.read(TestImages.png(folder.resolve("small.png"), 640, 360)).issues())
                .containsExactly(QualityIssue.TOO_SMALL);
        assertThat(ImageFiles.read(TestImages.png(folder.resolve("tall.png"), 500, 1500)).issues()).isEmpty();
    }

    @Test
    void aJpegSavedAtALowQualityIsTooCompressed() throws Exception {

        MediaImage image = ImageFiles.read(TestImages.jpeg(folder.resolve("blocky.jpg"), 1600, 900, 0.3f));

        assertThat(image.issues()).containsExactly(QualityIssue.TOO_COMPRESSED);
    }

    @Test
    void theEstimatedQualityIsCloseToTheOneTheJpegWasSavedAt() throws Exception {

        for (int quality : new int[]{25, 50, 75, 95})
            assertThat(ImageQuality.jpegQuality(
                    TestImages.jpeg(folder.resolve(quality + ".jpg"), 64, 64, quality / 100f)).orElseThrow())
                    .isBetween(quality - 2, quality + 2);
    }

    @Test
    void aFileThatIsNoJpegHasNoQuality() throws Exception {

        assertThat(ImageQuality.jpegQuality(new ByteArrayInputStream(new byte[]{1, 2, 3}))).isEmpty();
        assertThat(ImageQuality.jpegQuality(new ByteArrayInputStream(new byte[]{(byte) 0xFF, (byte) 0xD8}))).isEmpty();
    }

    @Test
    void theFormatIsReadFromTheContentNotTheName() throws Exception {

        Path png = TestImages.png(folder.resolve("named.jpg"), 10, 10);

        assertThat(ImageFiles.read(png).format()).isEqualTo(ImageFormat.PNG);
    }

    @Test
    void aFileThatIsNoJpegOrPngIsRefused() throws Exception {

        Path text = Files.writeString(folder.resolve("notes.png"), "not an image");
        Path gif = TestImages.write(folder.resolve("anim.gif"), 10, 10, "gif");

        assertThatThrownBy(() -> ImageFiles.read(text)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageFiles.read(gif)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("anim.gif is not a JPEG or PNG image");
    }
}
