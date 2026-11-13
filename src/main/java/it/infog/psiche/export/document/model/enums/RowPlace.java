package it.infog.psiche.export.document.model.enums;

/**
 * Where a row is in its table, which decides its rules: a table is ruled above, under its header row and at its
 * bottom, as printed books set it.
 */
public enum RowPlace {

    /** The header row, ruled above and under; a table split by a page repeats it on the next. */
    HEADER,
    /** A row of the body, without rules. */
    BODY,
    /** The body's last row, ruled under, closing the table. */
    LAST
}
