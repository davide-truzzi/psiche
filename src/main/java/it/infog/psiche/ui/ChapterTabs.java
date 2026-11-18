package it.infog.psiche.ui;

import it.infog.psiche.settings.model.enums.ChapterTab;
import it.infog.psiche.ui.git.HistoryPane;
import it.infog.psiche.ui.media.MediaPane;
import it.infog.psiche.ui.notes.NotesPane;
import it.infog.psiche.ui.preview.PreviewPane;

import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/**
 * The tabs beside the chapter: its preview, its notes, its images and its commit history. Each knows which
 * {@link ChapterTab} it is, for {@link OpeningTab} to pin, and each part draws only while its tab is the one shown.
 */
final class ChapterTabs {

    private ChapterTabs() {
    }

    static TabPane of(PreviewPane preview, NotesPane notes, MediaPane media, HistoryPane history) {

        Tab previewTab = tab("Preview", preview, ChapterTab.PREVIEW, preview.showingProperty());

        // "Notes (3)", in red-pencil while a note carries the Duplicate on Import tag; disabled with no book open.
        Tab notesTab = tab("Notes", notes, ChapterTab.NOTES, notes.showingProperty());
        notesTab.textProperty().bind(notes.titleBinding());
        notesTab.disableProperty().bind(notes.availableProperty().not());
        markWhile(notesTab, notes.duplicatesProperty(), "notes-duplicates");

        // "Media (5)", in red-pencil while the chapter's folder holds an image its text does not show.
        Tab mediaTab = tab("Media", media, ChapterTab.MEDIA, media.showingProperty());
        mediaTab.textProperty().bind(media.titleProperty());
        mediaTab.disableProperty().bind(media.availableProperty().not());
        markWhile(mediaTab, media.notInChapterProperty(), "media-not-in-chapter");

        Tab historyTab = tab("History", history, ChapterTab.HISTORY, history.showingProperty());
        historyTab.disableProperty().bind(history.availableProperty().not());

        TabPane tabs = new TabPane(previewTab, notesTab, mediaTab, historyTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("chapter-tabs");
        // A click on a pin in the editor's margin shows its note.
        notes.boardPins().setOnReveal(() -> tabs.getSelectionModel().select(notesTab));

        return tabs;
    }

    /** A tab showing {@code content}, which knows it is {@code kind} and is told whether it is on screen. */
    private static Tab tab(String name, Node content, ChapterTab kind, BooleanProperty showing) {

        Tab tab = new Tab(name, content);
        tab.setUserData(kind);
        showing.bind(tab.selectedProperty());

        return tab;
    }

    /** Gives {@code tab} the style class {@code mark} while {@code condition} holds. */
    private static void markWhile(Tab tab, ObservableBooleanValue condition, String mark) {

        condition.addListener((obs, had, has) -> {
            if (has)
                tab.getStyleClass().add(mark);
            else
                tab.getStyleClass().remove(mark);
        });
    }
}
