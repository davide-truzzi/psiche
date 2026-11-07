package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.Chapter;
import it.infog.psiche.markdown.HeadingAnchors;
import it.infog.psiche.markdown.ImageLines;
import it.infog.psiche.markdown.MarkdownFormatter;
import it.infog.psiche.markdown.TextLinks;
import it.infog.psiche.markdown.model.HeadingAnchor;
import it.infog.psiche.markdown.model.TextEdit;
import it.infog.psiche.markdown.model.TextLink;
import it.infog.psiche.markdown.model.TextRange;

import javafx.scene.control.IndexRange;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.function.Supplier;

import static it.infog.psiche.ui.common.Controls.menuItem;

/**
 * Insert link… and Remove link in the manuscript's right-click menu. On a link, Insert link… reads Modify link…, and
 * Remove link shows, which keeps the link's text: the link under the right-click in Default mode, the one the caret or
 * the selection is in in Advanced; see {@link Manuscript#menuSelection}. Ctrl+K, as in word processors, inserts or
 * modifies at the caret. A link is made from the selection, on one line and outside an image; each change is one edit,
 * which a single Undo reverts.
 */
final class LinkActions {

    static final KeyCombination SHORTCUT = new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN);

    private final Manuscript manuscript;
    private final Supplier<Optional<Chapter>> chapter;
    private final BookHeadings headings;
    private final MenuItem edit = menuItem("Insert link…", this::editAtMenu);
    private final MenuItem remove = menuItem("Remove link", this::remove);

    /**
     * @param chapter  the open chapter, if any
     * @param headings the book's chapters with their headings, which the dialog offers
     */
    LinkActions(Manuscript manuscript, Supplier<Optional<Chapter>> chapter, BookHeadings headings) {

        this.manuscript = manuscript;
        this.chapter = chapter;
        this.headings = headings;
        edit.setAccelerator(SHORTCUT);
    }

    /** The menu's items: Insert link… or Modify link…, then Remove link. */
    List<MenuItem> items() {

        return List.of(edit, remove);
    }

    /** Names, shows and enables the items for where the menu opens. */
    void update() {

        Optional<TextLink> link = linkAt(manuscript.menuSelection());
        edit.setText(link.isPresent() ? "Modify link…" : "Insert link…");
        edit.setDisable(!canEdit(link));
        remove.setVisible(link.isPresent());
        remove.setDisable(!manuscript.isEditable());
    }

    /** Ctrl+K: Insert link… or Modify link… at the caret or the selection, in both modes. */
    void edit() {

        edit(manuscript.selection());
    }

    /** The menu's Insert link… or Modify link…, on where the menu opened. */
    private void editAtMenu() {

        edit(manuscript.menuSelection());
    }

    /**
     * Insert link… or Modify link…: asks for the link's text and where it leads, and writes it in place of the link at
     * {@code target}, or of the selection; the caret ends after it.
     */
    private void edit(IndexRange target) {

        Optional<TextLink> link = linkAt(target);
        if (!canEdit(link))
            return;

        String text = manuscript.text();
        String name = chapter.get().orElseThrow().title();
        TextRange range = link.map(TextLink::range).orElseGet(this::trimmedSelection);
        LinkDialog dialog = new LinkDialog(manuscript.node().getScene().getWindow(), name, chapters(name, text),
                link.map(TextLink::text).orElse(text.substring(range.start(), range.end())),
                link.map(TextLink::destination));
        if (!dialog.showAndWait())
            return;

        // A link whose text stays keeps it as written, with its formatting and title.
        String markdown = link.filter(found -> found.text().equals(dialog.text()))
                .map(found -> TextLinks.relinked(text, found, dialog.destination()))
                .orElseGet(() -> TextLinks.markdown(dialog.text(), dialog.destination()));
        manuscript.replace(range.start(), range.end(), markdown);
        int caret = range.start() + markdown.length();
        manuscript.select(caret, caret);
    }

    /** Remove link: the link the menu is on becomes its text alone, as written, selected. */
    private void remove() {

        if (!manuscript.isEditable())
            return;

        String text = manuscript.text();
        linkAt(manuscript.menuSelection()).ifPresent(link -> {
            TextEdit edit = TextLinks.unlinked(text, link);
            manuscript.replace(edit.start(), edit.end(), edit.replacement());
            manuscript.select(edit.selectionStart(), edit.selectionEnd());
        });
    }

    /** True if a link can be inserted at the selection, or {@code link}, the one it is in, modified. */
    private boolean canEdit(Optional<TextLink> link) {

        if (!manuscript.isEditable() || chapter.get().isEmpty())
            return false;
        if (link.isPresent())
            return true;

        String text = manuscript.text();
        TextRange range = trimmedSelection();
        return MarkdownFormatter.spansOneLine(text, range.start(), range.end())
                && ImageLines.of(text).stream().noneMatch(image -> image.range().overlaps(range));
    }

    private Optional<TextLink> linkAt(IndexRange target) {

        return TextLinks.at(manuscript.text(), target.getStart(), target.getEnd());
    }

    /** The selection without the spaces it starts or ends with, which stay outside the link. */
    private TextRange trimmedSelection() {

        String text = manuscript.text();
        IndexRange selection = manuscript.selection();
        int start = selection.getStart();
        int end = selection.getEnd();
        while (start < end && Character.isWhitespace(text.charAt(start)))
            start++;
        while (end > start && Character.isWhitespace(text.charAt(end - 1)))
            end--;

        return new TextRange(start, end);
    }

    /**
     * The book's chapters with their headings as saved, but {@code name}'s, the open chapter, as its {@code text} is
     * now; it is there even before the book's headings are read.
     */
    private SequencedMap<String, List<HeadingAnchor>> chapters(String name, String text) {

        SequencedMap<String, List<HeadingAnchor>> chapters = new LinkedHashMap<>(headings.headings());
        chapters.put(name, HeadingAnchors.of(text));
        return chapters;
    }
}
