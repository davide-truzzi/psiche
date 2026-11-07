package it.infog.psiche.ui.chapters;

import it.infog.psiche.markdown.TextCounter;
import it.infog.psiche.markdown.model.TextCount;
import it.infog.psiche.ui.common.StatusBar;
import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * What the window tells about the open chapter and the book: in the status bar, the chapter's title, whether it is
 * saved, and its characters, words and pages, counted again once the author pauses typing; under the chapter list,
 * the {@link BookCounts}.
 */
final class ChapterStatus {

    // Counting parses the whole chapter, so wait for a pause in typing as the preview does, but less long.
    private static final Duration COUNT_DELAY = Duration.millis(300);

    private final ChapterEditor chapterEditor;
    private final StatusBar statusBar;
    private final BookCounts bookCounts;
    private final PauseTransition countDelay = new PauseTransition(COUNT_DELAY);

    /**
     * @param bookCountsShown    true to show the book's counts open, false collapsed
     * @param rememberBookCounts told when the author opens or collapses them, to keep it for the next start
     */
    ChapterStatus(ChapterEditor chapterEditor, StatusBar statusBar, boolean bookCountsShown,
                  Consumer<Boolean> rememberBookCounts) {

        this.chapterEditor = chapterEditor;
        this.statusBar = statusBar;
        bookCounts = new BookCounts(statusBar.pageMeasureProperty(), statusBar.pageMeasureMenu(), bookCountsShown,
                rememberBookCounts);
        countDelay.setOnFinished(event -> countChapter());
        statusBar.alignCountsTo(chapterEditor.textMarginProperty());
    }

    /** The book's counts, to place under the chapter list. */
    Node bookCounts() {

        return bookCounts.node();
    }

    /** Counts the open chapter once the author pauses typing; with none open, counts nothing. */
    void countLater() {

        if (chapterEditor.chapter().isPresent())
            countDelay.playFromStart();
        else
            countDelay.stop();
    }

    /** Shows the open chapter's characters, words and pages in the status bar now. */
    void countChapter() {

        countDelay.stop();
        if (chapterEditor.chapter().isPresent())
            statusBar.showCount(TextCounter.count(chapterEditor.text()));
    }

    /** Shows {@code counted}, the whole book's characters and words as saved, in the book's counts. */
    void showBookCount(TextCount counted) {

        bookCounts.show(counted);
    }

    /** Shows the open chapter's title and whether it is saved, or clears the chapter's part with none open. */
    void showChapter() {

        chapterEditor.chapter().ifPresentOrElse(
                chapter -> statusBar.showChapter(chapter.title(), chapterEditor.isDirty()),
                statusBar::clearChapter);
    }

    /** Clears the chapter's part and the book's counts, as no book is open. */
    void clear() {

        showChapter();
        bookCounts.clear();
    }
}
