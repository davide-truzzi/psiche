package it.infog.psiche.media;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Image files for the tests, drawn with a few shapes so a JPEG has something to compress. */
final class TestImages {

    private TestImages() {
    }

    static Path png(Path file, int width, int height) throws IOException {

        return write(file, width, height, "png");
    }

    /** Writes an image of {@code format}, as Java's image writers name it, such as {@code "gif"}. */
    static Path write(Path file, int width, int height, String format) throws IOException {

        ImageIO.write(drawing(width, height), format, file.toFile());
        return file;
    }

    /** Writes a JPEG saved at {@code quality}, from 0 to 1, as image programs offer it. */
    static Path jpeg(Path file, int width, int height, float quality) throws IOException {

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam parameters = writer.getDefaultWriteParam();
        parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parameters.setCompressionQuality(quality);

        Files.deleteIfExists(file);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(file.toFile())) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(drawing(width, height), null, null), parameters);
        } finally {
            writer.dispose();
        }

        return file;
    }

    private static BufferedImage drawing(int width, int height) {

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0xE9E0C8));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(new Color(0x5B4A33));
        graphics.drawLine(0, height, width, 0);
        graphics.fillOval(width / 4, height / 4, width / 2, height / 2);
        graphics.dispose();

        return image;
    }
}
