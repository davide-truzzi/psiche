package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.Chapter;
import it.infog.psiche.ui.common.Dialogs;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Before the open book is replaced, Git records it, a chapter is exported or the window closes: asks whether to save
 * or discard the unsaved chapters, so that what is on disk is exactly what the author decided to keep.
 */
public final class UnsavedChanges {

    private static final ButtonType SAVE_ALL = new ButtonType("Save all", ButtonData.YES);
    private static final ButtonType DISCARD = new ButtonType("Discard", ButtonData.NO);

    private final ChapterEditor chapterEditor;
    private final Supplier<Window> window;

    public UnsavedChanges(ChapterEditor chapterEditor, Supplier<Window> window) {

        this.chapterEditor = chapterEditor;
        this.window = window;
    }

    /** True if any chapter has changes that are not saved. */
    public boolean any() {

        return chapterEditor.hasUnsavedChanges();
    }

    /**
     * Lists the unsaved chapters under {@code question} and saves or discards them as the author chooses.
     *
     * @return false if the author cancelled or saving failed, in which case the caller should stop
     */
    public boolean settle(String question) {

        if (!chapterEditor.hasUnsavedChanges())
            return true;

        ButtonType choice = ask(question, SAVE_ALL, DISCARD, ButtonType.CANCEL);
        if (choice == SAVE_ALL)
            return chapterEditor.saveAll();

        if (choice == DISCARD) {
            chapterEditor.discardAll();
            return true;
        }

        return false;
    }

    /**
     * Tells the author, if some chapters have unsaved changes, that they must be saved or reverted before {@code what}
     * can be done, as it takes the book as it is on disk.
     *
     * @return true if it was refused, in which case the caller should stop
     */
    public boolean refuse(String what) {

        if (!chapterEditor.hasUnsavedChanges())
            return false;

        Dialogs.inform(window.get(), header(), titles() + "\n\nSave or revert them first, then " + what + ".");
        return true;
    }

    /** Lists the unsaved chapters under {@code question}; closing the dialog counts as Cancel. */
    private ButtonType ask(String question, ButtonType... choices) {

        return Dialogs.choose(window.get(), header(), titles() + "\n\n" + question, choices)
                .orElse(ButtonType.CANCEL);
    }

    private String header() {

        int count = chapterEditor.sortedUnsavedChapters().size();
        return count == 1 ? "1 chapter has unsaved changes" : "%d chapters have unsaved changes".formatted(count);
    }

    /** The unsaved chapters' titles, one to a line, in book order. */
    private String titles() {

        return chapterEditor.sortedUnsavedChapters().stream().map(Chapter::title).collect(Collectors.joining("\n"));
    }
}
