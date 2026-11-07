package it.infog.psiche.git.model;

import java.nio.file.Path;
import java.util.Optional;

/**
 * A file both devices changed in ways Synchronize could not merge. This device's version stays; a Markdown file, such
 * as a chapter, gets the other device's beside it as a copy, while any other file has it only in the history.
 *
 * @param file the file, as an absolute path; it may not be on disk, deleted on this device
 * @param copy the copy of the other device's version, as an absolute path; empty if there is none
 */
public record Conflict(Path file,
                       Optional<Path> copy) {
}
