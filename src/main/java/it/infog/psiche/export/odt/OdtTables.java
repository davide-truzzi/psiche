package it.infog.psiche.export.odt;

import it.infog.psiche.export.document.model.enums.Alignment;
import it.infog.psiche.export.document.model.enums.RowPlace;
import it.infog.psiche.export.document.model.enums.Style;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tables of {@code content.xml} as they are written into {@link OdtBody}'s text. Each is as wide as the text, its
 * columns evenly wide, and ruled as printed books set tables, by its cells' borders: above and under its header row,
 * and under its last row. The header row is kept in {@code table:table-header-rows}, which the word processor repeats
 * on every page the table runs over, and no row splits between pages, as in the PDF and the DOCX. A cell's paragraphs
 * take its column's alignment through an automatic style over Table Contents or Table Heading, which
 * {@code styles.xml} defines.
 */
final class OdtTables {

    // The room around a cell's text, as the PDF gives it, and no lines but the rules.
    private static final String CELL = "fo:padding-top=\"0.15cm\" fo:padding-bottom=\"0.15cm\" "
            + "fo:padding-left=\"0.3cm\" fo:padding-right=\"0.3cm\" fo:border-left=\"none\" fo:border-right=\"none\"";
    // The looks of the table and of its cells, automatic styles as LibreOffice writes them, given with the first table.
    // The table fills the A4 page's 16cm of text, from margin to margin.
    private static final String STYLES = """
            <style:style style:name="Table" style:family="table">
            <style:table-properties style:width="16cm" table:align="margins" fo:margin-top="0.8cm" \
            fo:margin-bottom="0.8cm"/></style:style>
            <style:style style:name="Table_Row" style:family="table-row">
            <style:table-row-properties fo:keep-together="always"/></style:style>
            <style:style style:name="Table_Heading_Cell" style:family="table-cell">
            <style:table-cell-properties %s fo:border-top="0.75pt solid #000000" \
            fo:border-bottom="0.5pt solid #000000"/></style:style>
            <style:style style:name="Table_Cell" style:family="table-cell">
            <style:table-cell-properties %s/></style:style>
            <style:style style:name="Table_Last_Cell" style:family="table-cell">
            <style:table-cell-properties %s fo:border-bottom="0.75pt solid #000000"/></style:style>
            """.formatted(CELL, CELL, CELL);

    private final StringBuilder xml;
    // The aligned paragraph styles, by name, each written once however many cells use it.
    private final Map<String, String> alignedStyles = new LinkedHashMap<>();

    // How many tables are written, which names each one in the document.
    private int tables;
    // The open table's columns' alignments, null outside a table, and the column of the cell open or written last.
    private List<Alignment> columns;
    private int column;
    private String cellStyle;
    private boolean inHeader;

    /** @param xml the body's text, which the tables are written into */
    OdtTables(StringBuilder xml) {

        this.xml = xml;
    }

    void open(List<Alignment> alignments) {

        tables++;
        columns = alignments;
        xml.append("<table:table table:name=\"Table%d\" table:style-name=\"Table\">\n".formatted(tables))
                .append("<table:table-column table:number-columns-repeated=\"%d\"/>\n".formatted(alignments.size()));
    }

    /** Opens a row, the header row inside {@code table:table-header-rows}, which closes before the first other row. */
    void openRow(RowPlace place) {

        boolean header = place == RowPlace.HEADER;
        if (header && !inHeader)
            xml.append("<table:table-header-rows>\n");
        if (!header && inHeader)
            xml.append("</table:table-header-rows>\n");
        inHeader = header;

        cellStyle = switch (place) {
            case HEADER -> "Table_Heading_Cell";
            case BODY -> "Table_Cell";
            case LAST -> "Table_Last_Cell";
        };
        column = -1;
        xml.append("<table:table-row table:style-name=\"Table_Row\">");
    }

    void openCell() {

        column++;
        xml.append("<table:table-cell table:style-name=\"%s\" office:value-type=\"string\">".formatted(cellStyle));
    }

    void closeCell() {

        xml.append("</table:table-cell>");
    }

    void closeRow() {

        xml.append("</table:table-row>\n");
    }

    void close() {

        if (inHeader)
            xml.append("</table:table-header-rows>\n");
        inHeader = false;
        columns = null;
        xml.append("</table:table>\n");
    }

    /**
     * The name of {@code style} for a paragraph written now: in a cell of a column set centre or right, that of the
     * automatic style aligning it; otherwise {@code style}'s own.
     */
    String styleName(Style style) {

        boolean inCell = style == Style.TABLE_CONTENTS || style == Style.TABLE_HEADING;
        if (columns == null || !inCell || columns.get(column) == Alignment.LEFT)
            return style.odtName();

        String textAlign = columns.get(column) == Alignment.CENTER ? "center" : "end";

        String name = style.odtName() + "_" + textAlign;
        alignedStyles.putIfAbsent(name, """
                <style:style style:name="%s" style:family="paragraph" style:parent-style-name="%s">\
                <style:paragraph-properties fo:text-align="%s"/></style:style>
                """.formatted(name, style.odtName(), textAlign));

        return name;
    }

    /** The automatic styles the tables written need, for {@code <office:automatic-styles>}; none without a table. */
    String automaticStyles() {

        return tables == 0 ? "" : STYLES + String.join("", alignedStyles.values());
    }
}
