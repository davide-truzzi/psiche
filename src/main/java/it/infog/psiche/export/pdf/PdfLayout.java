package it.infog.psiche.export.pdf;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.common.Template;
import it.infog.psiche.export.TitleMark;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.render.DefaultObjectDrawerFactory;
import com.openhtmltopdf.util.XRLog;
import org.apache.pdfbox.pdmodel.font.FontMappers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;

import static it.infog.psiche.export.XmlText.escape;

/**
 * Lays out an XHTML page as a PDF through OpenHTMLtoPDF, with Libertinus Serif embedded, for every PDF Psiche makes:
 * the manuscript and the writing history. Each brings its own stylesheet and body.
 */
public final class PdfLayout {

    private static final String FONTS = "/it/infog/psiche/export/pdf/fonts/";
    private static final Template PAGE = Template.load(PdfLayout.class, "page.xhtml");
    // Draws the Psiche mark under each chapter title in lines, where the XHTML has its object.
    private static final DefaultObjectDrawerFactory DRAWERS = new DefaultObjectDrawerFactory();

    static {
        // Keeps PDFBox from reading and caching every installed font; exports only draw with fonts they embed.
        FontMappers.set(new BundledFontMapper());
        // The renderer logs its progress at info level on every export; keep its warnings, such as a missing image.
        XRLog.listRegisteredLoggers().forEach(logger -> XRLog.setLevel(logger, Level.WARNING));
        DRAWERS.registerDrawer(TitleMark.OBJECT_TYPE, new TitleMarkDrawer());
    }

    private PdfLayout() {
    }

    /**
     * Writes the page as a PDF at {@code target}.
     *
     * @param title         the PDF's title, as a PDF reader shows it in the document's properties
     * @param book          the book, for the PDF's author and language
     * @param stylesheet    the page's CSS
     * @param head          more for the page's head, such as the bookmarks; empty for none
     * @param body          the page's XHTML body
     * @param baseDirectory the directory relative links and images resolve against
     */
    public static void write(String title, BookProperties book, String stylesheet, String head, String body,
                             Path baseDirectory, Path target) {

        String html = PAGE.fill(Map.of(
                "language", escape(book.language()),
                "title", escape(title),
                "authors", escape(String.join(", ", book.authors())),
                "style", stylesheet,
                "head", head,
                "body", body));

        try (OutputStream out = Files.newOutputStream(target)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // The program that made the PDF, as a PDF reader shows it in the document's properties.
            builder.withProducer("Psiche Writer");
            builder.useObjectDrawerFactory(DRAWERS);
            useFont(builder, "LibertinusSerif-Regular.ttf", 400, FontStyle.NORMAL);
            useFont(builder, "LibertinusSerif-Italic.ttf", 400, FontStyle.ITALIC);
            useFont(builder, "LibertinusSerif-Bold.ttf", 700, FontStyle.NORMAL);
            useFont(builder, "LibertinusSerif-BoldItalic.ttf", 700, FontStyle.ITALIC);
            builder.withHtmlContent(html, baseDirectory.toUri().toString());
            builder.toStream(out);
            builder.run();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + target, e);
        }
    }

    /** The resource {@code name}, such as a stylesheet, from the package of {@code owner}. */
    public static String readResource(Class<?> owner, String name) {

        try (InputStream in = owner.getResourceAsStream(name)) {
            if (in == null)
                throw new IllegalStateException("Missing resource " + name);

            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + name, e);
        }
    }

    /** {@code text} as the content of a CSS string in double quotes, such as a page header's. */
    public static String cssString(String text) {

        return text.replace("\\", "\\\\").replace("\"", "\\\"").replaceAll("[\\r\\n]+", " ");
    }

    /** Embeds one style of Libertinus Serif, read only if the PDF uses it. */
    private static void useFont(PdfRendererBuilder builder, String file, int weight, FontStyle style) {

        builder.useFont(() -> PdfLayout.class.getResourceAsStream(FONTS + file), "Libertinus Serif", weight, style,
                true);
    }
}
