package it.infog.psiche.export;

import it.infog.psiche.book.model.BookProperties;
import it.infog.psiche.export.docx.DocxWriter;
import it.infog.psiche.export.epub.EpubWriter;
import it.infog.psiche.export.model.BookExport;
import it.infog.psiche.export.model.ExportedChapter;
import it.infog.psiche.export.model.enums.EpubVariant;
import it.infog.psiche.export.odt.OdtWriter;
import it.infog.psiche.export.pdf.ManuscriptPdf;

import java.nio.file.Path;
import java.util.List;

/**
 * Writes the whole book to one file outside it: a title page, then every chapter starting on a new page. The book
 * comes from {@link BookAssembler}, with its chapters in order and empty ones already skipped or replaced.
 */
public final class BookExporter {

    private final ManuscriptPdf pdf = new ManuscriptPdf();
    private final OdtWriter odt = new OdtWriter();
    private final DocxWriter docx = new DocxWriter();
    private final EpubWriter epub = new EpubWriter();

    /**
     * Writes the book as a manuscript PDF, like a chapter's: a title page with the cover, title, subtitle and
     * authors, then the contents, both without a page header, then the chapters, numbered from 1 on the first
     * chapter's first page. The contents link to the chapters and show their pages, and are also the PDF's bookmarks.
     */
    public void manuscriptPdf(BookExport book, Path target) {

        //front matter
        StringBuilder body = new StringBuilder(XhtmlRenderer.titlePage(book.titlePage()));
        body.append(XhtmlRenderer.contents(book.contents(), id -> "#" + id, " id=\"toc\""));

        //chapters
        for (ExportedChapter chapter : book.chapters())
            body.append("<section class=\"chapter\" id=\"%s\">\n".formatted(chapter.id()))
                    .append(XhtmlRenderer.render(chapter.document()))
                    .append("</section>\n");

        //book properties
        BookProperties properties = book.properties();
        pdf.writeBook(properties.title(), ManuscriptHeader.of(properties), properties, book.contents(),
                body.toString(), book.baseDirectory(), target);
    }

    /**
     * Writes the book as a manuscript OpenDocument text file, laid out like the PDF: the title page, then every chapter
     * on a new page, in the named styles a chapter's ODT uses.
     */
    public void manuscriptOdt(BookExport book, Path target) {

        odt.writeBook(book, ManuscriptHeader.of(book.properties()), target);
    }

    /**
     * Writes the book as a Word document, laid out like the ODT: the title page and the contents without the header,
     * then every chapter on a new page, numbered from 1. The contents link to the chapters but show no pages, which
     * Word would only work out after asking the reader.
     */
    public void manuscriptDocx(BookExport book, Path target) {

        docx.writeBook(book, ManuscriptHeader.of(book.properties()), target);
    }

    /**
     * What would make the book's EPUB fall short of what stores ask for: a missing or small cover, images left out or
     * without a description. The author decides whether to fix them or export anyway.
     *
     * @return a sentence per problem; empty if there is none
     */
    public List<String> epubProblems(BookExport book, EpubVariant variant) {

        return epub.check(book, variant);
    }

    /**
     * Writes the book as an EPUB 3 for the stores: the cover (for every store but Kindle, which adds its own), the
     * title page, the contents, the chapters and the copyright notice.
     */
    public void epub(BookExport book, EpubVariant variant, Path target) {

        epub.write(book, variant, target);
    }
}
