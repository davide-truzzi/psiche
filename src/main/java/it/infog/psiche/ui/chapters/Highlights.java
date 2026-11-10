package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.model.TextRange;
import org.fxmisc.richtext.Selection;
import org.fxmisc.richtext.SelectionImpl;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Parts of the text drawn in one look, each as a selection of its own behind the text, as the repetitions are.
 * <p>
 * They are never styles of the text: restyling a paragraph in view makes RichTextFX measure it again, and its scroll
 * bar can then move the view, far from where the author is.
 */
final class Highlights {

    private final StyleClassedTextArea area;
    private final String style;
    private final List<Selection<Collection<String>, String, Collection<String>>> marks = new ArrayList<>();
    private List<TextRange> ranges = List.of();

    /** @param style the look of the parts in writer.css; it also names their selections, so give each its own */
    Highlights(StyleClassedTextArea area, String style) {

        this.area = area;
        this.style = style;
    }

    /** Draws {@code places} instead of those drawn now, unless they are the same. */
    void show(List<TextRange> places) {

        if (places.equals(ranges))
            return;

        ranges = places;
        // Those drawn already move to the new places, far cheaper than new ones, as when a key shifts them all.
        while (marks.size() > places.size())
            // Removing one also disposes of it.
            area.removeSelection(marks.removeLast());
        while (marks.size() < places.size()) {
            // Named apart: the area keeps its selections in a set ordered by name, where one name is one selection.
            var mark = new SelectionImpl<>(style + marks.size(), area, path -> path.getStyleClass().add(style));
            area.addSelection(mark);
            marks.add(mark);
        }
        for (int index = 0; index < places.size(); index++)
            marks.get(index).selectRange(places.get(index).start(), places.get(index).end());
    }
}
