package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.Chapter;
import it.infog.psiche.markdown.ImageLines;
import it.infog.psiche.markdown.model.TextEdit;

import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Changes made to the open chapter from outside its text, as the Media tab makes them: each is one change Undo
 * reverts, and none is made to a chapter no longer open, or while the text cannot be edited.
 */
public final class ChapterEdits {

    private final Manuscript manuscript;
    private final Predicate<Chapter> isOpen;

    /** @param isOpen true for the chapter open in {@code manuscript} */
    ChapterEdits(Manuscript manuscript, Predicate<Chapter> isOpen) {

        this.manuscript = manuscript;
        this.isOpen = isOpen;
    }

    /**
     * Rewrites the text of {@code target} with {@code rewrite}: only the stretch between the first and the last
     * character that differ is replaced, so the view stays where it is.
     *
     * @return false if {@code target} is no longer the open chapter, or cannot be edited now
     */
    public boolean change(Chapter target, UnaryOperator<String> rewrite) {

        if (!canEdit(target))
            return false;

        String text = manuscript.text();
        TextEdit edit = TextEdit.between(text, rewrite.apply(text));
        if (!edit.empty())
            manuscript.replace(edit.start(), edit.end(), edit.replacement());
        return true;
    }

    /**
     * Writes {@code image}, an image's Markdown, into {@code target} as a paragraph of its own after the caret's line;
     * see {@link ImageLines#insert}.
     *
     * @return false if {@code target} is no longer the open chapter, or cannot be edited now
     */
    public boolean insertImage(Chapter target, String image) {

        if (!canEdit(target))
            return false;

        TextEdit edit = ImageLines.insert(manuscript.text(), manuscript.caret(), image);
        manuscript.replace(edit.start(), edit.end(), edit.replacement());
        manuscript.select(edit.selectionStart(), edit.selectionEnd());
        return true;
    }

    private boolean canEdit(Chapter target) {

        return isOpen.test(target) && manuscript.isEditable();
    }
}
