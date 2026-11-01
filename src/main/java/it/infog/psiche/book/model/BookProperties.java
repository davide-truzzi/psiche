package it.infog.psiche.book.model;

import java.util.List;

/**
 * What an ebook needs to know about the book besides its chapters.
 *
 * @param title      the title; never blank
 * @param subtitle   the subtitle, or empty
 * @param authors    the authors' names, in the order they are credited
 * @param language   the language the book is written in, as a BCP 47 tag such as {@code it-IT}
 * @param rights     the copyright notice, such as "© 2026 Name Surname", or empty
 * @param identifier a unique id for the book, generated once, so every ebook built from it is the same book to readers
 */
public record BookProperties(String title,
                             String subtitle,
                             List<String> authors,
                             String language,
                             String rights,
                             String identifier) {

    public BookProperties {
        authors = List.copyOf(authors);
    }
}
