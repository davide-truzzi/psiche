package it.infog.psiche.export.pdf;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.export.model.Contents;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;
import org.apache.pdfbox.pdmodel.font.FontMappers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

import static it.infog.psiche.export.XmlText.escape;

/**
 * Lays out XHTML as a manuscript PDF through OpenHTMLtoPDF: A4, double-spaced, with the authors, the book's title and
 * the page number at the top of every page, and Libertinus Serif embedded. The look is in {@code manuscript.css}; the
 * whole book adds {@code book.css}, for its title page and the page break before every chapter.
 */
public final class ManuscriptPdf {

    private static final String FONTS = "/it/infog/psiche/export/pdf/fonts/";

    // XHTML, as the renderer reads it: the head carries the PDF's title, author and language.
    private static final String PAGE = """
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml" lang="%s">
            <head>
            <meta charset="utf-8" />
            <title>%s</title>
            <meta name="author" content="%s" />
            <style>%s</style>
            %s</head>
            <body>
            %s</body>
            </html>
            """;

    private final String chapterStyle;
    private final String bookStyle;

    static {
        // Keeps PDFBox from reading and caching every installed font; exports only draw with fonts they embed.
        FontMappers.set(new BundledFontMapper());
        // The renderer logs its progress at info level on every export; keep its warnings, such as a missing image.
        XRLog.listRegisteredLoggers().forEach(logger -> XRLog.setLevel(logger, Level.WARNING));
    }

    public ManuscriptPdf() {

        this.chapterStyle = readResource("manuscript.css");
        this.bookStyle = chapterStyle + readResource("book.css");
    }

    /**
     * Writes one chapter as a manuscript PDF.
     *
     * @param title         the PDF's title, as a PDF reader shows it in the document's properties
     * @param header        the text at the top left of every page, such as the authors and the book's title
     * @param book          the book, for the PDF's author and language
     * @param body          the chapter's XHTML
     * @param baseDirectory the directory relative links and images resolve against, the book's chapters
     */
    public void writeChapter(String title, String header, BookProperties book, String body, Path baseDirectory,
                             Path target) {

        write(chapterStyle, title, header, book, "", body, baseDirectory, target);
    }

    /**
     * Writes the whole book as a manuscript PDF. Its first page is the title page, and its second the contents, both
     * without a header; each chapter is a {@code <section class="chapter">}, starting on a new page, and the pages are
     * numbered from the first chapter. The contents are also the PDF's bookmarks, which PDF readers list beside the
     * pages.
     *
     * @param contents the book's contents, for the bookmarks
     * @param body     the title page's {@code <section class="title-page">}, the contents' {@code <nav class="toc">},
     *                 then the chapters' sections, each with its id
     * @see #writeChapter
     */
    public void writeBook(String title, String header, BookProperties book, Contents contents, String body,
                          Path baseDirectory, Path target) {

        write(bookStyle, title, header, book, bookmarks(contents), body, baseDirectory, target);
    }

    /** @param head more for the page's head, such as the bookmarks; empty for none */
    private void write(String stylesheet, String title, String header, BookProperties book, String head, String body,
                       Path baseDirectory, Path target) {

        String authors = String.join(", ", book.authors());
        String style = stylesheet.replace("{{header}}", cssString(header));
        String html = PAGE.formatted(escape(book.language()), escape(title), escape(authors), style, head, body);

        try (OutputStream out = Files.newOutputStream(target)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
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

    /** The PDF's bookmarks, as OpenHTMLtoPDF reads them from the head: the contents, then a bookmark per chapter. */
    private static String bookmarks(Contents contents) {

        StringBuilder bookmarks = new StringBuilder("<bookmarks>\n");
        bookmarks.append("<bookmark name=\"%s\" href=\"#toc\"/>\n".formatted(escape(contents.heading())));
        for (Contents.Entry entry : contents.entries())
            bookmarks.append("<bookmark name=\"%s\" href=\"#%s\"/>\n"
                    .formatted(escape(entry.title()), escape(entry.id())));

        return bookmarks.append("</bookmarks>\n").toString();
    }

    /** Embeds one style of Libertinus Serif, read only if the PDF uses it. */
    private static void useFont(PdfRendererBuilder builder, String file, int weight, FontStyle style) {

        builder.useFont(() -> ManuscriptPdf.class.getResourceAsStream(FONTS + file), "Libertinus Serif", weight,
                style, true);
    }

    /** {@code text} as the content of a CSS string in double quotes. */
    private static String cssString(String text) {

        return text.replace("\\", "\\\\").replace("\"", "\\\"").replaceAll("[\\r\\n]+", " ");
    }

    private static String readResource(String name) {

        try (InputStream in = ManuscriptPdf.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + name, e);
        }
    }
}
