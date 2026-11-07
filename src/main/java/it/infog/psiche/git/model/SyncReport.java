package it.infog.psiche.git.model;

import it.infog.psiche.git.model.enums.RemoteState;

import java.nio.file.Path;
import java.util.List;

/**
 * What Synchronize did.
 *
 * @param found     the remote compared with the book before: {@code SAME}, {@code EMPTY} or {@code BEHIND} (the book's
 *                  revisions sent), {@code AHEAD} (the remote's brought in), {@code DIVERGED} (merged, then sent)
 * @param changed   the files Synchronize changed on disk, as absolute paths: brought in, merged, deleted, or copies
 * @param conflicts the files both devices changed in ways that could not be merged, in the order of their paths
 */
public record SyncReport(RemoteState found,
                         List<Path> changed,
                         List<Conflict> conflicts) {
}
