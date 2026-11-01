package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.Chapter;
import it.infog.psiche.ui.common.Dialogs;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.util.List;
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

    /** Lists the unsaved chapters under {@code question}; closing the dialog counts as Cancel. */
    private ButtonType ask(String question, ButtonType... choices) {

        List<Chapter> unsaved = chapterEditor.sortedUnsavedChapters();

        String header = unsaved.size() == 1
                ? "1 chapter has unsaved changes"
                : "%d chapters have unsaved changes".formatted(unsaved.size());

        String titles = unsaved.stream().map(Chapter::title).collect(Collectors.joining("\n"));

        return Dialogs.choose(window.get(), header, titles + "\n\n" + question, choices).orElse(ButtonType.CANCEL);
    }
}
