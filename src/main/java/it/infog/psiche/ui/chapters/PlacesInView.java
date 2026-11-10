package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.model.TextRange;
import javafx.application.Platform;
import org.fxmisc.richtext.StyleClassedTextArea;

import java.util.List;

/**
 * Places of the text kept, of which only those in view are marked, through {@link Highlights}: RichTextFX gives each
 * paragraph shown a shape for every mark, and a common word has thousands of places. Those coming into view are
 * marked at once as the author scrolls, however fast, with no search again, once the view is laid out.
 */
final class PlacesInView {

    private final StyleClassedTextArea area;
    private final Highlights marks;
    // The places kept, in the order of the text.
    private List<TextRange> places = List.of();
    // True while the places in view wait for the view to be laid out after a scroll.
    private boolean showPending;

    /** @param style the marks' look in writer.css, as {@link Highlights} takes it */
    PlacesInView(StyleClassedTextArea area, String style) {

        this.area = area;
        marks = new Highlights(area, style);
        // Other paragraphs come into view, with places kept already.
        area.estimatedScrollYProperty().addListener((obs, old, top) -> showInViewLater());
    }

    /** Keeps {@code places} instead of those kept now, in the order of the text, and marks those in view. */
    void show(List<TextRange> places) {

        this.places = places;
        showInView();
    }

    /** Shows the places in view once the scroll is over and the view laid out: asked for meanwhile, it is not yet. */
    private void showInViewLater() {

        if (showPending || places.isEmpty())
            return;

        showPending = true;
        Platform.runLater(() -> {
            showPending = false;
            showInView();
        });
    }

    /** Marks the places kept that are in view; with none kept, the view is not asked for, as while the text changes. */
    private void showInView() {

        if (places.isEmpty()) {
            marks.show(List.of());
            return;
        }

        TextRange view = view();
        marks.show(places.stream()
                .filter(range -> range.start() >= view.start() && range.end() <= view.end())
                .toList());
    }

    /** The text of the paragraphs in view, even partly; none before the text is laid out. */
    private TextRange view() {

        if (area.getVisibleParagraphs().isEmpty())
            return new TextRange(0, 0);

        int first = area.firstVisibleParToAllParIndex();
        int last = area.lastVisibleParToAllParIndex();
        return new TextRange(area.getAbsolutePosition(first, 0),
                area.getAbsolutePosition(last, area.getParagraphLength(last)));
    }
}
