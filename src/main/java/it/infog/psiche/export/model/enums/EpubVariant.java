package it.infog.psiche.export.model.enums;

/** Which stores an EPUB is made for, since Amazon wants the cover left to it. */
public enum EpubVariant {

    /** Apple Books, Kobo, Google Play Books and the distributors: the cover is the book's first page. */
    STORES,
    /**
     * Amazon Kindle: no cover inside the book, since KDP adds the one uploaded with the book's details, and a cover in
     * the book as well would show twice. A smaller file also costs less to deliver.
     */
    KINDLE
}
