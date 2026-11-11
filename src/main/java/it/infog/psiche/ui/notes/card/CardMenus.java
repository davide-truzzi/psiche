package it.infog.psiche.ui.notes.card;

import it.infog.psiche.notes.model.enums.NoteColor;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static it.infog.psiche.ui.common.Controls.menuItem;

/** The menus of a {@link NoteCard}: its own, on a right-click, and its file's, from the paperclip. */
final class CardMenus {

    private CardMenus() {
    }

    /**
     * The note's menu, on a right-click: the file menu, then the colours, on a chapter's board the pin's actions, Move
     * to chapter and Detach.
     */
    static List<MenuItem> items(NoteCard card, NoteCard.Board board) {

        List<MenuItem> items = new ArrayList<>(fileItems(card, board));
        Menu colors = new Menu("Colour");
        for (NoteColor each : NoteColor.values())
            colors.getItems().add(menuItem(each.label(), () -> card.recolor(each)));
        items.addAll(List.of(new SeparatorMenuItem(), colors));

        board.pins().ifPresent(pins -> {
            items.add(new SeparatorMenuItem());
            items.add(menuItem("Pin to current paragraph", () -> pins.pinHere(card), pins.canPinHere()));
            if (pins.isPinned(card))
                items.addAll(List.of(menuItem("Go to paragraph", () -> pins.goTo(card)),
                        menuItem("Unpin", () -> pins.unpin(card))));
            items.add(new SeparatorMenuItem());
        });

        NoteMoveActions moves = board.moves();
        moves.otherBoard().ifPresent(name ->
                items.add(menuItem("Move to " + name, () -> moves.moveToOtherBoard(card))));
        items.addAll(List.of(menuItem("Move to chapter…", () -> moves.moveToChapter(card), moves.hasOtherChapters()),
                menuItem("Detach", () -> board.delete(card))));
        return items;
    }

    /** The file menu: with a file, Open, Show in folder, Replace file, Remove file; without, Attach file and Paste. */
    static List<MenuItem> fileItems(NoteCard card, NoteCard.Board board) {

        List<MenuItem> items = new ArrayList<>();
        if (card.attachment().isPresent()) {
            items.add(menuItem("Open", () -> board.files().open(card), Files.exists(card.attachment().get())));
            items.add(menuItem("Show in folder", () -> board.files().showInFolder(card)));
            items.add(menuItem("Replace file…", () -> board.files().chooseFile(card)));
            items.add(menuItem("Remove file", () -> board.files().remove(card)));
        } else {
            items.add(menuItem("Attach file…", () -> board.files().chooseFile(card)));
            items.add(menuItem("Paste", () -> board.files().paste(card)));
        }
        return items;
    }
}
