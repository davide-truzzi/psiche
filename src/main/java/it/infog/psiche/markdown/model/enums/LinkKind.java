package it.infog.psiche.markdown.model.enums;

/** What a link leads to. */
public enum LinkKind {

    /** A web or mail address: {@code http:}, {@code https:} or {@code mailto:}. */
    WEB,

    /** A chapter of the book, or a heading in it. */
    CHAPTER,

    /** Nothing Psiche can follow: another scheme, a web address without a host, a file that is no chapter. */
    MALFORMED
}
