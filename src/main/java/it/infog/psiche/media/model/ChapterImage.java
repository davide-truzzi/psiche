package it.infog.psiche.media.model;

import it.infog.psiche.markdown.model.ImageLine;

import java.util.List;

/**
 * An image in a chapter's folder, with the paragraphs of the chapter that show it, in order; none if the chapter does
 * not show it, which the Media tab marks as not in the chapter.
 */
public record ChapterImage(MediaImage image,
                           List<ImageLine> uses) {

    /** True if the chapter shows the image at least once. */
    public boolean inChapter() {

        return !uses.isEmpty();
    }

    /** The caption the chapter first shows the image with; empty if it does not show it. */
    public String caption() {

        return uses.isEmpty() ? "" : uses.getFirst().caption();
    }
}
