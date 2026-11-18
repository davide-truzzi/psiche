package it.infog.psiche.update.model;

/**
 * A release of Psiche Writer, as psiche.app announces it.
 *
 * @param version     its name as people read it, as "0.9.1 Beta"
 * @param tag         its Git tag, as "v0.9.1-beta"
 * @param downloadUrl the site's download page, to open in the browser
 */
public record Release(String version,
                      String tag,
                      String downloadUrl) {
}
