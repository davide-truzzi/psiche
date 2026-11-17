package it.infog.psiche.ui.export;

import it.infog.psiche.export.model.BrokenReference;
import it.infog.psiche.ui.common.Dialogs;

import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tells the author, before an export, which images and links of the chapters it would leave out, chapter by chapter
 * with the line each is on, and asks whether to export anyway or cancel to fix them first.
 */
final class ReferenceWarning {

    private static final ButtonType EXPORT_ANYWAY = new ButtonType("Export anyway", ButtonData.YES);
    // More would make the dialog taller than a screen; the rest are counted.
    private static final int MAX_LISTED = 12;
    private static final String INDENT = "    ";

    private ReferenceWarning() {
    }

    /** True if nothing would be left out, or the author chose to export anyway. */
    static boolean accept(Window window, List<BrokenReference> found) {

        if (found.isEmpty())
            return true;

        long chapters = found.stream().map(BrokenReference::chapter).distinct().count();
        String header = (chapters == 1 ? "1 chapter has" : "%d chapters have".formatted(chapters))
                + " images or links the export leaves out";
        String content = String.join("\n", listed(found)) + "\n\nImages that cannot be shown are left out, and links "
                + "that lead nowhere become plain text. Export anyway, or cancel to fix them first? In the editor, "
                + "Alt+L goes to a line.";

        Optional<ButtonType> choice = Dialogs.choose(window, header, content, EXPORT_ANYWAY, ButtonType.CANCEL);
        return choice.filter(EXPORT_ANYWAY::equals).isPresent();
    }

    /** The first of {@code found}, each under its chapter's name, then how many more there are. */
    private static List<String> listed(List<BrokenReference> found) {

        List<String> lines = new ArrayList<>();
        String chapter = null;
        for (BrokenReference reference : found.subList(0, Math.min(found.size(), MAX_LISTED))) {
            if (!reference.chapter().equals(chapter)) {
                chapter = reference.chapter();
                lines.add(chapter);
            }
            lines.add(INDENT + "line %d: %s".formatted(reference.line(), problem(reference)));
        }

        int more = found.size() - MAX_LISTED;
        if (more > 0)
            lines.add(more == 1 ? "and 1 more" : "and %d more".formatted(more));
        return lines;
    }

    private static String problem(BrokenReference reference) {

        return switch (reference.problem()) {
            case MISSING_IMAGE -> "image %s is not there".formatted(reference.destination());
            case UNUSABLE_IMAGE -> "image %s is of a type this format cannot show".formatted(reference.destination());
            case BROKEN_LINK -> "link to %s leads nowhere".formatted(reference.destination());
        };
    }
}
