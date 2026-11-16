package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.MarkedLines;
import it.infog.psiche.markdown.model.TextRange;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The numbers of the manuscript's lines, the lines of the text as written, in its left margin while the author asks
 * for them: on the first line each paragraph in view shows, on its baseline and centred in the margin, 70% as high as
 * the text, smaller if the widest in view would not fit the margin. They take the place of the comments' and the
 * mistakes' dots on the lines, and their colours: brass for a line with a comment, red for one with a spelling
 * mistake, half brass and half red for one with both. A layer over the text the mouse goes through, which does
 * nothing while hidden.
 */
final class LineNumbers {

    /** A number to show: its line, and where its baseline goes, in the layer. */
    private record Line(int index,
                        double baseline) {
    }

    // The numbers' size, against the text's.
    private static final double SIZE = 0.7;
    // Room kept free on each side of the widest number in the margin, in pixels.
    private static final double SIDE_ROOM = 2;
    private static final String FONT_SIZE_STYLE = "-fx-font-size: %.1fpx;";
    private static final String LAYER = "line-numbers";
    private static final String NUMBER = "line-number";
    private static final String COMMENT = "line-number-comment";
    private static final String MISTAKE = "line-number-mistake";
    private static final String BOTH = "line-number-both";

    private final StyleClassedTextArea area;
    private final Pane layer = new Pane();
    private final List<Text> numbers = new ArrayList<>();
    // As wide as the widest number in view, never shown: it measures the size that fits the margin.
    private final Text widest = new Text();
    // One line of the text, never shown: it measures where the text's baseline is.
    private final Text textLine = new Text("0");
    private final AfterLayout layout;
    private List<TextRange> comments = List.of();
    private List<TextRange> mistakes = List.of();
    private BitSet commentLines = new BitSet();
    private BitSet mistakeLines = new BitSet();
    private boolean shown;
    // True from a change in the comments or the mistakes until their lines are found again.
    private boolean marksOutdated;

    LineNumbers(StyleClassedTextArea area) {

        this.area = area;
        layer.getStyleClass().add(LAYER);
        layer.setMouseTransparent(true);
        layer.setVisible(false);
        widest.getStyleClass().addAll(NUMBER, BOTH);
        for (Text measure : List.of(widest, textLine)) {
            measure.setVisible(false);
            measure.setManaged(false);
        }
        layer.getChildren().addAll(widest, textLine);
        layout = new AfterLayout(area, layer, () -> shown, this::place);
    }

    /** The layer, to stack over the text, the same size as it. */
    Node node() {

        return layer;
    }

    /** Shows the numbers, or hides them. */
    void setShown(boolean shown) {

        if (shown == this.shown)
            return;

        this.shown = shown;
        layer.setVisible(shown);
        layout.place();
    }

    /** Colours the numbers of the lines {@code comments} and {@code mistakes} are on, both as the text has them now. */
    void setMarks(List<TextRange> comments, List<TextRange> mistakes) {

        if (comments == this.comments && mistakes == this.mistakes)
            return;

        this.comments = comments;
        this.mistakes = mistakes;
        marksOutdated = true;
        layout.place();
    }

    /** Places the numbers where the text's lines are now, after a layout. */
    private void place() {

        //the lines the comments and the mistakes are on, found again only once they change
        if (marksOutdated) {
            String text = area.getText();
            commentLines = MarkedLines.of(text, comments);
            mistakeLines = MarkedLines.of(text, mistakes);
            marksOutdated = false;
        }

        //where a paragraph's first baseline is, below its top, from the text in view; none while nothing is laid out
        Optional<Text> text = firstText();
        if (text.isEmpty() || !(text.get().getParent() instanceof TextFlow paragraph)) {
            showLines(List.of(), 0);
            return;
        }
        Font font = text.get().getFont();
        textLine.setFont(font);
        double firstBaseline = paragraph.getLineSpacing() / 2 + textLine.getBaselineOffset();

        //each paragraph whose first line is in view
        Insets padding = area.getPadding();
        double top = padding.getTop();
        double bottom = area.getHeight() - padding.getBottom();
        List<Line> lines = new ArrayList<>();
        for (int visible = 0; visible < area.getVisibleParagraphs().size(); visible++) {
            double baseline = layer.screenToLocal(area.getVisibleParagraphBoundsOnScreen(visible)).getMinY()
                    + firstBaseline;
            if (baseline >= top && baseline <= bottom)
                lines.add(new Line(area.visibleParToAllParIndex(visible), baseline));
        }

        showLines(lines, lines.isEmpty() ? 0 : fittingSize(font.getSize() * SIZE, lines.getLast().index()));
    }

    /** One number on each of {@code lines}, {@code size} pixels high, reusing the numbers already there. */
    private void showLines(List<Line> lines, double size) {

        while (numbers.size() < lines.size())
            numbers.add(number());

        double left = layer.sceneToLocal(area.localToScene(0, 0)).getX();
        double width = area.getPadding().getLeft();
        String style = sizeStyle(size);
        for (int i = 0; i < numbers.size(); i++) {
            Text number = numbers.get(i);
            number.setVisible(i < lines.size());
            if (i >= lines.size())
                continue;

            Line line = lines.get(i);
            number.setText(String.valueOf(line.index() + 1));
            number.setStyle(style);
            List<String> classes = classes(line.index());
            if (!number.getStyleClass().equals(classes))
                number.getStyleClass().setAll(classes);
            number.setWrappingWidth(width);
            number.setLayoutX(left);
            number.setLayoutY(line.baseline());
        }
    }

    /**
     * The size of the numbers: {@code wanted}, or less if a number as long as {@code lastLine}'s, the widest in view,
     * would not fit the margin.
     */
    private double fittingSize(double wanted, int lastLine) {

        widest.setText("0".repeat(String.valueOf(lastLine + 1).length()));
        widest.setStyle(sizeStyle(wanted));
        widest.applyCss();
        double width = widest.getLayoutBounds().getWidth();
        double room = area.getPadding().getLeft() - 2 * SIDE_ROOM;
        return width > room ? wanted * room / width : wanted;
    }

    /** The style classes of the number of {@code line}, which give its colour. */
    private List<String> classes(int line) {

        boolean comment = commentLines.get(line);
        boolean mistake = mistakeLines.get(line);
        if (comment && mistake)
            return List.of(NUMBER, BOTH);
        if (comment)
            return List.of(NUMBER, COMMENT);
        if (mistake)
            return List.of(NUMBER, MISTAKE);

        return List.of(NUMBER);
    }

    /**
     * The first text of the paragraphs in view, whose font and paragraph give the text's size, baseline and line
     * spacing; none while nothing is laid out. Besides its text, a paragraph holds the shapes of its backgrounds,
     * underlines and caret, often first.
     */
    private Optional<Text> firstText() {

        return area.lookupAll(".paragraph-text").stream()
                .flatMap(paragraph -> paragraph instanceof TextFlow flow ? flow.getChildren().stream() : Stream.empty())
                .filter(Text.class::isInstance)
                .map(Text.class::cast)
                .findFirst();
    }

    private Text number() {

        Text number = new Text();
        number.setTextAlignment(TextAlignment.CENTER);
        number.setTextOrigin(VPos.BASELINE);
        layer.getChildren().add(number);
        return number;
    }

    private static String sizeStyle(double size) {

        return String.format(Locale.ROOT, FONT_SIZE_STYLE, size);
    }
}
