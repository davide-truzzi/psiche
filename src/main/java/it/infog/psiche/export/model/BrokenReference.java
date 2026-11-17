package it.infog.psiche.export.model;

import it.infog.psiche.export.model.enums.ReferenceProblem;

/**
 * An image or a link of a chapter that the export would leave out.
 *
 * @param chapter     the chapter's file title
 * @param line        the line it is on, from 1, as the editor's line numbers count them
 * @param destination the image's file or the link's destination, as written
 */
public record BrokenReference(String chapter,
                              int line,
                              ReferenceProblem problem,
                              String destination) {
}
