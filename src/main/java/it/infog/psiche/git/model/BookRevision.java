package it.infog.psiche.git.model;

import java.time.Instant;
import java.util.List;

/**
 * One commit that changed the book's chapters, as the writing history shows it.
 *
 * @param commitId the full commit id, which a reader can check against the book's history
 * @param shortId  the abbreviated commit id, as Git shows it in a log
 * @param message  the whole commit message, every line of it
 * @param time     when the commit was made, by the clock of the author's computer
 * @param merge    true if the commit merged the work of another computer; its changes are then only those the author
 *                 made by hand while merging, and may be none
 * @param changes  the chapters the commit changed, in the order of their paths
 */
public record BookRevision(String commitId,
                           String shortId,
                           String message,
                           Instant time,
                           boolean merge,
                           List<ChapterChange> changes) {

    public BookRevision {
        changes = List.copyOf(changes);
    }
}
