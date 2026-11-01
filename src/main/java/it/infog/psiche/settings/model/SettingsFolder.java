package it.infog.psiche.settings.model;

import java.nio.file.Path;

/**
 * Where the app keeps its settings.
 *
 * @param path     the folder, which may not exist yet
 * @param portable true if it travels with the app, as a {@code settings} folder next to it on a USB stick does; the
 *                 books it lists are then remembered relative to it, as a drive's letter or mount point may change
 */
public record SettingsFolder(Path path,
                             boolean portable) {
}
