package it.infog.psiche.export;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The Psiche mark that closes each chapter title of the PDF and the EPUB: the brand kit's mark, with its dots and
 * ring, from {@code psiche-mark.svg}, drawn in lines rather than as a letter of the font. The file is kept bare, with
 * no title or comment, since the EPUB carries it as it is.
 */
public final class TitleMark {

    /** The type of the {@code <object>} that stands for the mark in the PDF's XHTML, for the renderer to draw. */
    public static final String OBJECT_TYPE = "application/x-psiche-mark";

    private static final String RESOURCE = "psiche-mark.svg";
    private static final String SOURCE = read();
    // The colour the EPUB's mark had when it was a letter.
    private static final String EBOOK_COLOUR = "#b8361f";
    // Styled here rather than in the book's stylesheet, which the author may have written before the mark existed.
    private static final String EBOOK_STYLE = "display: block; width: 0.63em; height: 0.9em; margin: 0.3em auto 0;";

    private TitleMark() {
    }

    /** The mark's SVG, drawn in {@code currentColor}. */
    public static String svg() {

        return SOURCE;
    }

    /** For the manuscript: an object the PDF's renderer draws, sized by the manuscript's stylesheet. */
    static String manuscript() {

        return "<object type=\"%s\" class=\"title-mark\" aria-hidden=\"true\"></object>".formatted(OBJECT_TYPE);
    }

    /** For the ebook: the SVG itself, in the red pencil's colour, hidden from screen readers. */
    static String ebook() {

        return SOURCE.replace("currentColor", EBOOK_COLOUR).replaceFirst("<svg ",
                "<svg class=\"title-mark\" aria-hidden=\"true\" style=\"%s\" ".formatted(EBOOK_STYLE));
    }

    private static String read() {

        try (InputStream in = TitleMark.class.getResourceAsStream(RESOURCE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }
}
