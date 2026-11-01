package it.infog.psiche.export;

import it.infog.psiche.book.model.BookProperties;

/** The line at the top of every manuscript page, in the PDF and the ODT alike. */
final class ManuscriptHeader {

    private ManuscriptHeader() {
    }

    /** The authors and the book's title, such as "Anna Bianchi — La strada del sale", or the title if it has none. */
    static String of(BookProperties book) {

        String authors = String.join(", ", book.authors());
        return authors.isEmpty() ? book.title() : authors + " — " + book.title();
    }
}
