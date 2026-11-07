package it.infog.psiche.markdown.model;

/**
 * A heading of a chapter, which links can lead to.
 *
 * @param level  from 1, the chapter's title, to 6
 * @param text   the heading as a reader sees it
 * @param anchor the name a link gives after {@code #} to lead to it, unique in its chapter
 */
public record HeadingAnchor(int level,
                            String text,
                            String anchor) {
}
