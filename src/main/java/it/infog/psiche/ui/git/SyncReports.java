package it.infog.psiche.ui.git;

import it.infog.psiche.common.ChapterFolders;
import it.infog.psiche.git.model.Conflict;
import it.infog.psiche.git.model.SyncReport;
import it.infog.psiche.ui.common.Dialogs;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/** What the author is told about Synchronize: to commit first, what it did, and the changes it could not merge. */
final class SyncReports {

    private SyncReports() {
    }

    /** Asks the author to commit before Synchronize, which did nothing. */
    static void commitFirst(Window owner) {

        Dialogs.inform(owner, "Commit before synchronizing",
                "Synchronize sends the book's revisions and brings in those of your other devices, so it needs your "
                        + "work committed: write what you changed beside Commit, click Commit, then Synchronize.");
    }

    /** What Synchronize did, for the status bar: the chapters it changed, if any. */
    static String summary(SyncReport report) {

        String chapters = report.changed().stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(name -> name.endsWith(ChapterFolders.CHAPTER_EXTENSION))
                .map(SyncReports::title)
                .collect(Collectors.joining(", "));
        String changed = chapters.isEmpty() ? "" : ": " + chapters;

        return switch (report.found()) {
            case SAME -> "Synchronized: the book and the remote were the same";
            case EMPTY, BEHIND -> "Synchronized: the book's revisions are sent";
            case AHEAD -> "Brought in from the remote" + changed;
            default -> "Merged with the remote" + changed;
        };
    }

    /** Tells the author which files both devices changed and where the other device's version is; nothing if none. */
    static void showConflicts(Window owner, List<Conflict> conflicts) {

        if (conflicts.isEmpty())
            return;

        String lines = conflicts.stream().map(SyncReports::line).collect(Collectors.joining("\n"));
        Dialogs.inform(owner, "Both devices changed the same lines",
                "Your version stays in the book. Compare each copy with its chapter, keep what you want, then delete "
                        + "the copy.\n\n" + lines);
    }

    /** One file both devices changed, and where the other device's version is. */
    private static String line(Conflict conflict) {

        String name = conflict.file().getFileName().toString();
        return conflict.copy()
                .map(copy -> "• %s: the other device's version is in \"%s\"".formatted(title(name),
                        title(copy.getFileName().toString())))
                .orElse("• %s: this device's version is kept; the other's is in the history".formatted(name));
    }

    /** {@code name} without {@code .md}, as a chapter's title. */
    private static String title(String name) {

        return name.endsWith(ChapterFolders.CHAPTER_EXTENSION)
                ? name.substring(0, name.length() - ChapterFolders.CHAPTER_EXTENSION.length())
                : name;
    }
}
