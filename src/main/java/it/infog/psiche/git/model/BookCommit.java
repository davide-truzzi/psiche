package it.infog.psiche.git.model;

import java.time.Instant;

/**
 * One commit of the book, as a list of every revision shows it.
 *
 * @param commitId the full commit id, to read the book as it was then
 * @param shortId  the abbreviated commit id to show to the author
 * @param message  the first line of the commit message
 * @param time     when the commit was made, by the clock of the computer that made it
 */
public record BookCommit(String commitId,
                         String shortId,
                         String message,
                         Instant time) {
}
