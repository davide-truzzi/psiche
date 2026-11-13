package it.infog.psiche.export.docx;

import it.infog.psiche.export.document.model.enums.Alignment;
import it.infog.psiche.export.document.model.enums.RowPlace;
import it.infog.psiche.export.document.model.enums.Style;

import java.util.List;

/**
 * The tables of {@code word/document.xml} as they are written into {@link DocxBody}'s text. Each is as wide as the
 * text, its columns evenly wide, and ruled as printed books set tables, by its cells' borders: above and under its
 * header row, and under its last row. The header row is repeated on every page the table runs over, and no row is
 * split between pages. A cell's paragraphs take its column's alignment directly, over Table Contents or Table Heading,
 * which {@code styles.xml} defines.
 */
final class DocxTables {

    // The A4 page's text is 9070 twentieths of a point wide, between its margins (see DocxBody's page).
    private static final int TEXT_WIDTH = 9070;
    // The table's width, the whole text's, in fiftieths of a percent; the room around a cell's text, as the PDF gives.
    private static final String TABLE_PROPERTIES = """
            <w:tblPr><w:tblW w:w="5000" w:type="pct"/><w:tblLayout w:type="fixed"/><w:tblCellMar>\
            <w:top w:w="85" w:type="dxa"/><w:left w:w="170" w:type="dxa"/><w:bottom w:w="85" w:type="dxa"/>\
            <w:right w:w="170" w:type="dxa"/></w:tblCellMar><w:tblLook w:val="0000"/></w:tblPr>""";
    // The rules, in eighths of a point: 0.75pt above and under the table, 0.5pt under its header row.
    private static final String RULE = "w:val=\"single\" w:sz=\"%d\" w:space=\"0\" w:color=\"000000\"";
    private static final String HEADER_BORDERS = "<w:tcBorders><w:top %s/><w:bottom %s/></w:tcBorders>"
            .formatted(RULE.formatted(6), RULE.formatted(4));
    private static final String LAST_BORDERS = "<w:tcBorders><w:bottom %s/></w:tcBorders>".formatted(RULE.formatted(6));

    private final StringBuilder xml;

    // The open table's columns' alignments, null outside a table, and the column of the cell open or written last.
    private List<Alignment> columns;
    private int column;
    private int columnWidth;
    // The borders of the open row's cells.
    private String borders;

    /** @param xml the body's text, which the tables are written into */
    DocxTables(StringBuilder xml) {

        this.xml = xml;
    }

    void open(List<Alignment> alignments) {

        columns = alignments;
        columnWidth = TEXT_WIDTH / alignments.size();
        xml.append("<w:tbl>").append(TABLE_PROPERTIES).append("<w:tblGrid>")
                .append("<w:gridCol w:w=\"%d\"/>".formatted(columnWidth).repeat(alignments.size()))
                .append("</w:tblGrid>\n");
    }

    /** Opens a row that never splits between pages; the header row is repeated on every page the table runs over. */
    void openRow(RowPlace place) {

        borders = switch (place) {
            case HEADER -> HEADER_BORDERS;
            case BODY -> "";
            case LAST -> LAST_BORDERS;
        };
        column = -1;
        xml.append("<w:tr><w:trPr><w:cantSplit/>").append(place == RowPlace.HEADER ? "<w:tblHeader/>" : "")
                .append("</w:trPr>");
    }

    void openCell() {

        column++;
        xml.append("<w:tc><w:tcPr><w:tcW w:w=\"%d\" w:type=\"dxa\"/>%s</w:tcPr>".formatted(columnWidth, borders));
    }

    void closeCell() {

        xml.append("</w:tc>");
    }

    void closeRow() {

        xml.append("</w:tr>\n");
    }

    void close() {

        columns = null;
        xml.append("</w:tbl>\n");
    }

    /** True while a table is written: its paragraphs are in its cells. */
    boolean writing() {

        return columns != null;
    }

    /**
     * The paragraph property that aligns a paragraph of {@code style} written now as its column asks, in a cell of a
     * column set centre or right; otherwise none.
     */
    String justification(Style style) {

        boolean inCell = style == Style.TABLE_CONTENTS || style == Style.TABLE_HEADING;
        if (columns == null || !inCell || columns.get(column) == Alignment.LEFT)
            return "";

        return "<w:jc w:val=\"%s\"/>".formatted(columns.get(column) == Alignment.CENTER ? "center" : "right");
    }
}
