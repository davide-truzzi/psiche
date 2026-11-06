package it.infog.psiche.ui.chapters;

import it.infog.psiche.spelling.SpellChecker;
import it.infog.psiche.ui.common.StatusBar;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The manuscript's spell checking: on or off and how strict, as Preferences asks, in the open book's language, with
 * the words the author made right. The {@link Spelling} hands over the dictionary; the manuscript is checked again
 * each time it, the strictness or the author's own words change.
 */
final class ChapterSpelling {

    private final Manuscript manuscript;
    private final StatusBar statusBar;
    private final Supplier<Set<String>> ownWords;
    private final Consumer<String> addOwnWord;
    private final Spelling spelling = new Spelling(this::check);
    private Optional<SpellChecker> checker = Optional.empty();
    private boolean strict;

    /**
     * @param ownWords   the words the author made right, read when the manuscript is checked
     * @param addOwnWord adds a word to them, for every book
     */
    ChapterSpelling(Manuscript manuscript, StatusBar statusBar, Supplier<Set<String>> ownWords,
                    Consumer<String> addOwnWord) {

        this.manuscript = manuscript;
        this.statusBar = statusBar;
        this.ownWords = ownWords;
        this.addOwnWord = addOwnWord;
    }

    /** Turns spell checking on or off; with it on, checks the words with a capital letter too if {@code strict}. */
    void setOn(boolean on, boolean strict) {

        boolean strictChanged = strict != this.strict;
        this.strict = strict;
        spelling.setOn(on);
        if (strictChanged)
            check(checker);
    }

    /** Checks the spelling in the open book's language, a BCP 47 tag; none with {@code null} while no book is open. */
    void setBookLanguage(String tag) {

        spelling.setBookLanguage(tag);
    }

    /** Makes {@code word} right in every book, and checks the text again, where it is right at once. */
    void makeRight(String word) {

        addOwnWord.accept(word);
        check(checker);
        statusBar.info("\"%s\" will not be marked as a mistake again, in any book".formatted(word));
    }

    private void check(Optional<SpellChecker> checker) {

        this.checker = checker;
        manuscript.checkSpelling(checker.orElse(null), checker.isPresent() ? ownWords.get() : Set.of(), strict);
    }
}
