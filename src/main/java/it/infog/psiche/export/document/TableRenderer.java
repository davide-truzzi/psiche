package it.infog.psiche.export.document;

import it.infog.psiche.export.document.model.enums.Alignment;
import it.infog.psiche.export.document.model.enums.RowPlace;
import it.infog.psiche.export.document.model.enums.Style;

import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Writes a table into a {@link DocumentBody}, for {@link ChapterRenderer}: its columns aligned as its first row's
 * cells, each row told where it is, so the format rules the table as printed books do, and each cell a paragraph, the
 * header row's in {@link Style#TABLE_HEADING}, the body's in {@link Style#TABLE_CONTENTS}.
 */
final class TableRenderer {

    private TableRenderer() {
    }

    /**
     * @param body        where the table is written
     * @param cellContent writes a cell's text, emphasis, links and notes included, as the chapter's other text
     */
    static void render(TableBlock table, DocumentBody body, Consumer<Node> cellContent) {

        List<TableRow> rows = new ArrayList<>();
        for (Node section = table.getFirstChild(); section != null; section = section.getNext())
            for (Node row = section.getFirstChild(); row != null; row = row.getNext())
                rows.add((TableRow) row);

        body.openTable(alignments(rows.getFirst()));
        for (TableRow row : rows) {
            RowPlace place = rowPlace(row, rows);
            Style style = place == RowPlace.HEADER ? Style.TABLE_HEADING : Style.TABLE_CONTENTS;
            body.openRow(place);
            for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext())
                cell(cell, style, body, cellContent);
            body.closeRow();
        }
        body.closeTable();
    }

    /** Each column's alignment, as {@code row}'s cells give it; a column whose colons ask for nothing is left. */
    private static List<Alignment> alignments(TableRow row) {

        List<Alignment> alignments = new ArrayList<>();
        for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext())
            alignments.add(alignment(((TableCell) cell).getAlignment()));

        return alignments;
    }

    private static Alignment alignment(TableCell.Alignment alignment) {

        return switch (alignment) {
            case CENTER -> Alignment.CENTER;
            case RIGHT -> Alignment.RIGHT;
            case null, default -> Alignment.LEFT;
        };
    }

    private static RowPlace rowPlace(TableRow row, List<TableRow> rows) {

        if (row.getParent() instanceof TableHead)
            return RowPlace.HEADER;

        return row == rows.getLast() ? RowPlace.LAST : RowPlace.BODY;
    }

    /** A cell as a paragraph of {@code style}. */
    private static void cell(Node cell, Style style, DocumentBody body, Consumer<Node> cellContent) {

        body.openCell();
        body.openParagraph(style);
        cellContent.accept(cell);
        body.closeParagraph();
        body.closeCell();
    }
}
