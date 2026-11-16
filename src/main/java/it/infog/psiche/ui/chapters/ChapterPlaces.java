package it.infog.psiche.ui.chapters;

import it.infog.psiche.book.BookState;
import it.infog.psiche.book.Chapter;
import it.infog.psiche.book.model.ChapterPlace;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Where the author left each chapter of the open book, kept in its {@value BookState#FILE}, so a chapter opens again
 * there: the open chapter's place is remembered as another opens, as the book closes, and before it is renamed. The
 * chapter opened last is kept there too, so the book opens on it again.
 */
final class ChapterPlaces {

    private final Manuscript manuscript;
    private final Supplier<Chapter> open;

    // The open book's state, where the places are kept; the Notes tab keeps its own there too, so the two share it.
    private BookState state;

    /** @param open the chapter open in {@code manuscript}; {@code null} while none is */
    ChapterPlaces(Manuscript manuscript, Supplier<Chapter> open) {

        this.manuscript = manuscript;
        this.open = open;
    }

    /** Takes {@code state}, the open book's, where the places are kept; {@code null} as the book closes. */
    void setBookState(BookState state) {

        this.state = state;
    }

    /** Remembers where the author is in the open chapter; nothing if no chapter is open, or it is gone. */
    void remember() {

        Chapter chapter = open.get();
        if (chapter != null && state != null)
            state.savePlace(chapter, manuscript.place());
    }

    /** Remembers {@code chapter}, just opened, as the one the book opens on next time. */
    void opened(Chapter chapter) {

        if (state != null)
            state.saveLastChapter(chapter);
    }

    /** Moves where {@code from} was left to {@code to}, the name it was just renamed to. */
    void renamed(Chapter from, Chapter to) {

        if (state != null)
            state.rename(from, to);
    }

    /** Where {@code chapter} was left; its start if it was never opened. */
    ChapterPlace of(Chapter chapter) {

        return Optional.ofNullable(state).flatMap(kept -> kept.place(chapter)).orElse(ChapterPlace.START);
    }
}
