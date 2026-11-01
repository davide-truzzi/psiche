package it.infog.psiche.git.model;

import java.time.Instant;

/**
 * A chapter file that a commit deleted and that is not in the book now.
 *
 * @param path          the file's path in the repository
 * @param lastCommitId  the full id of the commit before the deletion, which holds the chapter's last version
 * @param deletedInId   the abbreviated id of the commit that deleted it, to show to the author
 * @param deletedAt     when that commit was made
 */
public record DeletedChapter(String path,
                             String lastCommitId,
                             String deletedInId,
                             Instant deletedAt) {
}
