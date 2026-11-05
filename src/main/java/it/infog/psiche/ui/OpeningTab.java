package it.infog.psiche.ui;

import it.infog.psiche.settings.Settings;
import it.infog.psiche.settings.model.enums.ChapterTab;
import it.infog.psiche.ui.common.StatusBar;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.util.EnumMap;
import java.util.Map;

/**
 * The tab beside the chapter that shows each time a book opens: the preview, until the author pins another with
 * <b>Show when a book opens</b> in that tab's right-click menu. Unpinning a tab gives the place back to the preview,
 * which stays pinned while no other is.
 */
final class OpeningTab {

    private static final String PIN = "Show when a book opens";

    private final TabPane tabs;
    private final Map<ChapterTab, Tab> byKind = new EnumMap<>(ChapterTab.class);
    private final Settings settings;
    private final StatusBar statusBar;

    private ChapterTab pinned;

    /** @param tabs the tabs beside the chapter, in the order {@link ChapterTab} lists them */
    OpeningTab(TabPane tabs, Settings settings, StatusBar statusBar) {

        this.tabs = tabs;
        for (ChapterTab kind : ChapterTab.values())
            byKind.put(kind, tabs.getTabs().get(kind.ordinal()));
        this.settings = settings;
        this.statusBar = statusBar;
        pinned = settings.openingTab();

        byKind.forEach(this::addMenu);
    }

    /** Shows the pinned tab, as a book opens. */
    void show() {

        tabs.getSelectionModel().select(byKind.get(pinned));
    }

    /** Gives {@code tab} a right-click menu that pins it, ticked while it is the pinned one. */
    private void addMenu(ChapterTab kind, Tab tab) {

        CheckMenuItem pin = new CheckMenuItem(PIN);
        pin.setOnAction(event -> pin(pin.isSelected() ? kind : ChapterTab.PREVIEW));

        ContextMenu menu = new ContextMenu(pin);
        // The preview cannot be unpinned: some tab has to show.
        menu.setOnShowing(event -> {
            pin.setSelected(pinned == kind);
            pin.setDisable(pinned == kind && kind == ChapterTab.PREVIEW);
        });
        tab.setContextMenu(menu);
    }

    private void pin(ChapterTab kind) {

        pinned = kind;
        settings.saveOpeningTab(kind);
        statusBar.info(kind.label() + " shows when a book opens");
    }
}
