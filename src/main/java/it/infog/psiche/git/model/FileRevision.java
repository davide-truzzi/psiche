package it.infog.psiche.git.model;

import java.time.Instant;

/**
 * One commit that changed a file, with how many lines of that file it added and removed.
 *
 * @param commitId    the full commit id, to read the file as it was in this commit
 * @param shortId     the abbreviated commit id to show to the author
 * @param path        the file's path in the repository as of this commit, which differs from today's before a rename
 * @param renamedFrom the path the file had before this commit renamed or copied it; {@code null} for other commits
 */
public record FileRevision(String commitId,
                           String shortId,
                           String path,
                           String renamedFrom,
                           String message,
                           Instant time,
                           int linesAdded,
                           int linesRemoved) {
}
