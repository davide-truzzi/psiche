package it.infog.psiche.export.docx.model;

/**
 * A list of a DOCX being written, whose items are still to come.
 *
 * @param id    its instance in {@code numbering.xml}
 * @param level how deep it is nested, from 0
 */
public record OpenList(int id,
                       int level) {
}
