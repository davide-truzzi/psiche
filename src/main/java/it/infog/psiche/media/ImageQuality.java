package it.infog.psiche.media;

import it.infog.psiche.media.model.enums.ImageFormat;
import it.infog.psiche.media.model.enums.QualityIssue;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Tells whether an image will look poor in the exports: too small, under {@value #MIN_SIDE} pixels on its longer side,
 * about 150 dpi across a 15 cm text width; or, for a JPEG, too compressed, saved at a quality under
 * {@value #MIN_JPEG_QUALITY}. The quality is estimated from the JPEG's own quantisation table, the way the IJG library
 * that most programs use derives that table from the quality it is given.
 */
final class ImageQuality {

    static final int MIN_SIDE = 900;
    static final int MIN_JPEG_QUALITY = 60;

    // JPEG markers: start of image, quantisation tables, start of scan, and those standing alone without a length.
    private static final int SOI = 0xD8;
    private static final int DQT = 0xDB;
    private static final int SOS = 0xDA;
    private static final int TEM = 0x01;
    private static final int RST0 = 0xD0;
    private static final int RST7 = 0xD7;
    private static final int TABLE_SIZE = 64;
    // The IJG library's luminance table at quality 50, in natural order; other qualities scale it.
    private static final int[] STANDARD_LUMINANCE = {
            16, 11, 10, 16, 24, 40, 51, 61,
            12, 12, 14, 19, 26, 58, 60, 55,
            14, 13, 16, 24, 40, 57, 69, 56,
            14, 17, 22, 29, 51, 87, 80, 62,
            18, 22, 37, 56, 68, 109, 103, 77,
            24, 35, 55, 64, 81, 104, 113, 92,
            49, 64, 78, 87, 103, 121, 120, 101,
            72, 92, 95, 98, 112, 100, 103, 99};
    // Where each value of a table, as a JPEG stores it in zigzag order, sits in natural order.
    private static final int[] ZIGZAG = {
            0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5,
            12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28,
            35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51,
            58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63};

    private ImageQuality() {
    }

    /** What will make the image in {@code file}, of {@code format} and {@code width} by {@code height}, look poor. */
    static Set<QualityIssue> of(Path file, ImageFormat format, int width, int height) throws IOException {

        Set<QualityIssue> issues = EnumSet.noneOf(QualityIssue.class);
        if (Math.max(width, height) < MIN_SIDE)
            issues.add(QualityIssue.TOO_SMALL);
        if (format == ImageFormat.JPEG && jpegQuality(file).orElse(100) < MIN_JPEG_QUALITY)
            issues.add(QualityIssue.TOO_COMPRESSED);

        return issues;
    }

    /** The quality, 1 to 100, the JPEG in {@code file} was most likely saved at; empty if it has no luminance table. */
    static OptionalInt jpegQuality(Path file) throws IOException {

        try (InputStream in = Files.newInputStream(file)) {
            return jpegQuality(in);
        }
    }

    /** As {@link #jpegQuality(Path)}, reading the JPEG from {@code in} up to its first scan. */
    static OptionalInt jpegQuality(InputStream in) throws IOException {

        DataInputStream jpeg = new DataInputStream(new BufferedInputStream(in));
        try {
            if (jpeg.readUnsignedByte() != 0xFF || jpeg.readUnsignedByte() != SOI)
                return OptionalInt.empty();

            while (true) {
                int marker = nextMarker(jpeg);
                if (marker == SOS)
                    return OptionalInt.empty();
                if (marker == TEM || (marker >= RST0 && marker <= RST7))
                    continue;

                int length = jpeg.readUnsignedShort() - 2;
                if (marker != DQT) {
                    jpeg.skipNBytes(length);
                    continue;
                }

                OptionalInt quality = luminanceQuality(jpeg, length);
                if (quality.isPresent())
                    return quality;
            }
        } catch (EOFException e) {
            return OptionalInt.empty();
        }
    }

    /** The next marker's code, after the 0xFF that starts it and any 0xFF filling before it. */
    private static int nextMarker(DataInputStream jpeg) throws IOException {

        int code = jpeg.readUnsignedByte();
        while (code != 0xFF)
            code = jpeg.readUnsignedByte();
        while (code == 0xFF)
            code = jpeg.readUnsignedByte();

        return code;
    }

    /**
     * Reads the tables of a quantisation segment of {@code length} bytes, and estimates the quality from its luminance
     * table, the first, if the segment has it.
     */
    private static OptionalInt luminanceQuality(DataInputStream jpeg, int length) throws IOException {

        OptionalInt quality = OptionalInt.empty();
        int left = length;
        while (left > 0) {
            int header = jpeg.readUnsignedByte();
            boolean wide = (header >> 4) == 1;
            int table = header & 0x0F;
            int[] natural = new int[TABLE_SIZE];
            for (int i = 0; i < TABLE_SIZE; i++)
                natural[ZIGZAG[i]] = wide ? jpeg.readUnsignedShort() : jpeg.readUnsignedByte();
            left -= 1 + TABLE_SIZE * (wide ? 2 : 1);

            if (table == 0)
                quality = OptionalInt.of(quality(natural));
        }

        return quality;
    }

    /**
     * The quality that scales the standard table to {@code table}: the IJG library multiplies it by a factor of
     * {@code 5000 / quality} percent under 50, and of {@code 200 - 2 * quality} percent from 50 up.
     */
    private static int quality(int[] table) {

        double scale = 0;
        for (int i = 0; i < TABLE_SIZE; i++)
            scale += table[i] * 100.0 / STANDARD_LUMINANCE[i];
        scale /= TABLE_SIZE;

        double quality = scale <= 100 ? (200 - scale) / 2 : 5000 / scale;
        return Math.clamp(Math.round(quality), 1, 100);
    }
}
