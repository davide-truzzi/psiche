package it.infog.psiche.ui.git;

import it.infog.psiche.git.TrackedChanges;
import it.infog.psiche.git.model.Comparison;
import it.infog.psiche.git.model.FileRevision;
import it.infog.psiche.git.model.Paragraph;
import it.infog.psiche.git.model.Segment;
import it.infog.psiche.ui.common.Dialogs;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A past version of a chapter compared with the saved chapter as tracked changes, the way a word processor shows them:
 * the chapter reads as it is now, with what was written since the version underlined and what was removed since then
 * struck through. The author can restore the version from here.
 */
public final class RevisionDialog {

    private static final ButtonType RESTORE = new ButtonType("Restore this version", ButtonData.OK_DONE);
    private static final ButtonType CLOSE = new ButtonType("Close", ButtonData.CANCEL_CLOSE);
    private static final String LEGEND = "Underlined: written since then  ·  Struck through: removed since then";
    // Room left above the first change when the dialog scrolls to it, so it is not flush with the edge.
    private static final double CONTEXT_ABOVE = 48;

    private RevisionDialog() {
    }

    /**
     * Shows how the saved chapter differs from the chapter as {@code revision} recorded it, and waits for the author.
     *
     * @param text         the chapter in that revision; empty if the chapter did not exist then, which leaves nothing
     *                     to restore
     * @param savedText    the chapter as it is on disk now
     * @param unsavedEdits whether the editor has changes that are not on disk, and so not part of the comparison
     * @return true if the author chose to restore the version
     */
    public static boolean show(Window owner, FileRevision revision, Optional<String> text, String savedText,
                               boolean unsavedEdits) {

        Dialog<ButtonType> dialog = new Dialog<>();
        Dialogs.ownAndStyle(dialog, owner);
        dialog.setResizable(true);
        dialog.setHeaderText(revision.message());
        dialog.getDialogPane().getStyleClass().add("revision-dialog");
        dialog.getDialogPane().getButtonTypes().setAll(CLOSE, RESTORE);

        // Named as the chapter was called in that version, which differs from today's name before a rename.
        String version = "%s at %s, %s".formatted(HistoryPane.chapterTitle(revision.path()), revision.shortId(),
                HistoryPane.COMMIT_TIME.format(revision.time()));
        Label summary = new Label();
        summary.getStyleClass().add("revision-summary");

        if (text.isEmpty()) {
            summary.setText(version + "\nThis chapter did not exist yet in this version.");
            dialog.getDialogPane().lookupButton(RESTORE).setDisable(true);
            dialog.getDialogPane().setContent(content(summary));
            return dialog.showAndWait().filter(RESTORE::equals).isPresent();
        }

        Comparison comparison = TrackedChanges.compare(text.get(), savedText);
        String note = unsavedEdits ? "\nYour unsaved edits are not included." : "";
        summary.setText(version + "\n" + describe(comparison) + note);
        dialog.getDialogPane().lookupButton(RESTORE).setDisable(comparison.identical());

        //the chapter as tracked changes, scrolled to the first one once the dialog is laid out
        VBox page = page(comparison);
        ScrollPane scroll = new ScrollPane(page);
        scroll.getStyleClass().add("revision-scroll");
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(760);
        scroll.setPrefViewportHeight(520);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        firstChange(page, comparison)
                .ifPresent(first -> dialog.setOnShown(event -> Platform.runLater(() -> scrollTo(scroll, first))));

        Label legend = new Label(LEGEND);
        legend.getStyleClass().add("revision-legend");
        legend.setVisible(!comparison.identical());

        dialog.getDialogPane().setContent(content(summary, scroll, legend));
        return dialog.showAndWait().filter(RESTORE::equals).isPresent();
    }

    private static VBox content(Node... children) {

        VBox content = new VBox(children);
        content.getStyleClass().add("revision-content");
        return content;
    }

    /** The changes in words a writer would use, such as "Since this version you changed 1 paragraph and added 1." */
    static String describe(Comparison comparison) {

        if (comparison.identical())
            return "This version is the same as your saved chapter.";

        List<String> parts = new ArrayList<>();
        addPart(parts, "changed", comparison.changed());
        addPart(parts, "added", comparison.added());
        addPart(parts, "removed", comparison.removed());
        if (parts.isEmpty())
            return "Since this version only blank lines and spacing have changed.";

        String joined = parts.size() == 1
                ? parts.getFirst()
                : String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast();
        return "Since this version you %s.".formatted(joined);
    }

    /** The first part names what is counted ("changed 2 paragraphs"); the ones after it only give the number. */
    private static void addPart(List<String> parts, String verb, int count) {

        if (count == 0)
            return;

        if (parts.isEmpty())
            parts.add("%s %d %s".formatted(verb, count, count == 1 ? "paragraph" : "paragraphs"));
        else
            parts.add("%s %d".formatted(verb, count));
    }

    /** One wrapping text flow per paragraph, each run of text styled by how it changed. */
    private static VBox page(Comparison comparison) {

        VBox page = new VBox();
        page.getStyleClass().add("revision-text");
        for (Paragraph paragraph : comparison.paragraphs()) {
            TextFlow flow = new TextFlow();
            flow.getStyleClass().add("revision-paragraph");
            for (Segment segment : paragraph.segments())
                flow.getChildren().add(words(segment.text(), segment.change()));

            // An empty flow has no height; a space keeps blank lines between paragraphs visible.
            if (paragraph.segments().stream().allMatch(segment -> segment.text().isEmpty()))
                flow.getChildren().setAll(words(" ", Segment.Change.UNCHANGED));
            page.getChildren().add(flow);
        }
        return page;
    }

    private static Text words(String text, Segment.Change change) {

        Text words = new Text(text);
        words.getStyleClass().addAll("revision-words", change.name().toLowerCase(Locale.ROOT));
        return words;
    }

    private static Optional<Node> firstChange(VBox page, Comparison comparison) {

        List<Paragraph> paragraphs = comparison.paragraphs();
        for (int i = 0; i < paragraphs.size(); i++)
            if (paragraphs.get(i).changed())
                return Optional.of(page.getChildren().get(i));
        return Optional.empty();
    }

    /** Scrolls so {@code paragraph} sits near the top of the viewport, with a little context above it. */
    private static void scrollTo(ScrollPane scroll, Node paragraph) {

        double contentHeight = scroll.getContent().getBoundsInLocal().getHeight();
        double viewportHeight = scroll.getViewportBounds().getHeight();
        if (contentHeight <= viewportHeight)
            return;

        double top = Math.max(0, paragraph.getBoundsInParent().getMinY() - CONTEXT_ABOVE);
        scroll.setVvalue(Math.min(1, top / (contentHeight - viewportHeight)));
    }
}
