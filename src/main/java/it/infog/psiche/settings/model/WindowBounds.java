package it.infog.psiche.settings.model;

/**
 * Where the main window was and how large, in screen coordinates, so it opens there again.
 *
 * @param maximized true if it filled the screen; the other values are then where it goes once restored
 */
public record WindowBounds(double x,
                           double y,
                           double width,
                           double height,
                           boolean maximized) {
}
