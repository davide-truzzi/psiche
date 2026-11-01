package it.infog.psiche.export.pdf;

import org.apache.fontbox.FontBoxFont;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.font.CIDFontMapping;
import org.apache.pdfbox.pdmodel.font.FontMapper;
import org.apache.pdfbox.pdmodel.font.FontMapping;
import org.apache.pdfbox.pdmodel.font.PDCIDSystemInfo;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Answers PDFBox's requests for installed fonts with the bundled Libertinus Serif. Exports embed every font they draw
 * with, but the PDF renderer still creates the 14 standard PDF fonts as fallbacks, and for each of them PDFBox's own
 * mapper would read every font installed on the computer and cache them in the home folder, taking seconds on the
 * first export.
 */
final class BundledFontMapper implements FontMapper {

    private static final String FONT = "/it/infog/psiche/export/pdf/fonts/LibertinusSerif-Regular.ttf";

    private TrueTypeFont font;

    @Override
    public FontMapping<TrueTypeFont> getTrueTypeFont(String baseFont, PDFontDescriptor fontDescriptor) {

        return new FontMapping<>(font(), true);
    }

    @Override
    public FontMapping<FontBoxFont> getFontBoxFont(String baseFont, PDFontDescriptor fontDescriptor) {

        return new FontMapping<>(font(), true);
    }

    @Override
    public CIDFontMapping getCIDFont(String baseFont, PDFontDescriptor fontDescriptor, PDCIDSystemInfo cidSystemInfo) {

        return new CIDFontMapping(null, font(), true);
    }

    /** The font, read the first time PDFBox asks for one. */
    private synchronized TrueTypeFont font() {

        if (font != null)
            return font;

        try (InputStream in = BundledFontMapper.class.getResourceAsStream(FONT)) {
            font = new TTFParser().parse(new RandomAccessReadBuffer(in));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + FONT, e);
        }

        return font;
    }
}
