package it.infog.psiche.markdown.model;

import it.infog.psiche.markdown.model.enums.LinkKind;

/**
 * Where a link leads, read from its destination.
 *
 * @param kind    a web address, a chapter of the book, or neither
 * @param chapter for a link to a chapter, the chapter's name, its file name without {@code .md}; empty otherwise
 * @param anchor  for a link to a chapter, the anchor of the heading it leads to; empty for the chapter's start, and
 *                for the other kinds
 */
public record LinkTarget(LinkKind kind,
                         String chapter,
                         String anchor) {

    public static final LinkTarget WEB = new LinkTarget(LinkKind.WEB, "", "");
    public static final LinkTarget MALFORMED = new LinkTarget(LinkKind.MALFORMED, "", "");

    /** A link to {@code anchor} in {@code chapter}, or to its start with an empty anchor. */
    public static LinkTarget chapter(String chapter, String anchor) {

        return new LinkTarget(LinkKind.CHAPTER, chapter, anchor);
    }
}
