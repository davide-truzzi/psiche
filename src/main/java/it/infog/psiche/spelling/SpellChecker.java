package it.infog.psiche.spelling;

import it.infog.psiche.spelling.model.Word;
import it.infog.psiche.spelling.model.SpellingLanguage;
import org.apache.lucene.analysis.hunspell.Dictionary;
import org.apache.lucene.analysis.hunspell.Hunspell;
import org.apache.lucene.analysis.hunspell.TimeoutPolicy;
import org.apache.lucene.store.ByteBuffersDirectory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.ParseException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks the spelling of words in one language, with the Hunspell dictionary the app brings for it, and suggests
 * others for a word it does not know. Making one reads the dictionary, which takes a moment, so it is done off the
 * FX thread; after that, a word takes microseconds and suggestions a few milliseconds. Safe to use from any thread.
 * <p>
 * A word is right if the dictionary knows it, or if it is one of the author's own words, as written there or with a
 * capital letter, as at the start of a sentence, or all in capitals. The curly apostrophe writers type (’) counts as
 * the straight one (') the dictionaries know, and a final apostrophe that is a closing quote is let go.
 */
public final class SpellChecker {

    private static final int SUGGESTIONS = 5;
    // Long enough for a long word's suggestions, short enough that a right-click never seems to hang.
    private static final long SUGGESTION_TIME_MILLIS = 250;
    private static final String CURLY_APOSTROPHE = "’";
    private static final String APOSTROPHE = "'";
    // Lucene names the files it builds the dictionary in with it; they are kept in memory here.
    private static final String WORK_FILES = "hunspell";

    private final SpellingLanguage language;
    private final Hunspell hunspell;

    private SpellChecker(SpellingLanguage language, Hunspell hunspell) {

        this.language = language;
        this.hunspell = hunspell;
    }

    /**
     * Reads the dictionary of {@code language}.
     *
     * @throws IllegalStateException if the app's dictionary cannot be read, which a build that lost it would cause
     */
    public static SpellChecker load(SpellingLanguage language) {

        try (InputStream affix = resource(language.affixFile()); InputStream words = resource(language.wordsFile())) {
            Dictionary dictionary = new Dictionary(new ByteBuffersDirectory(), WORK_FILES, affix, words);
            return new SpellChecker(language,
                    new Hunspell(dictionary, TimeoutPolicy.RETURN_PARTIAL_RESULT, () -> {}));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the %s dictionary".formatted(language), e);
        } catch (ParseException e) {
            throw new IllegalStateException("The %s dictionary is damaged".formatted(language), e);
        }
    }

    public SpellingLanguage language() {

        return language;
    }

    /** True if {@code word} is spelt right, by the dictionary or as one of {@code ownWords}. */
    public synchronized boolean isRight(String word, Collection<String> ownWords) {

        return isKnown(straight(word), straightened(ownWords));
    }

    /** The words of {@code markdown}'s prose that are spelt wrong, by the dictionary and {@code ownWords}, in order. */
    public synchronized List<Word> mistakes(String markdown, Collection<String> ownWords) {

        Set<String> own = straightened(ownWords);
        return ProseWords.of(markdown).stream()
                .filter(word -> !isKnown(straight(word.text()), own))
                .toList();
    }

    /**
     * Up to five words the author may have meant by {@code word}, the likeliest first; with its curly apostrophe if
     * it had one. Splitting it in two is one of them, but not leaving a bare part of a compound, as "-haus".
     */
    public synchronized List<String> suggestions(String word) {

        boolean curly = word.contains(CURLY_APOSTROPHE);
        return hunspell.suggest(straight(word), SUGGESTION_TIME_MILLIS).stream()
                .filter(suggestion -> !suggestion.startsWith("-") && !suggestion.endsWith("-"))
                .map(suggestion -> curly ? suggestion.replace(APOSTROPHE, CURLY_APOSTROPHE) : suggestion)
                .limit(SUGGESTIONS)
                .toList();
    }

    /** True if {@code word}, with straight apostrophes, is known, with or without a final apostrophe. */
    private boolean isKnown(String word, Set<String> own) {

        if (isKnownAsIs(word, own))
            return true;

        return word.endsWith(APOSTROPHE) && isKnownAsIs(word.substring(0, word.length() - 1), own);
    }

    private boolean isKnownAsIs(String word, Set<String> own) {

        if (word.isEmpty() || isOwn(word, own))
            return true;

        return hunspell.spell(word);
    }

    /** True if {@code word} is one of the author's words, or one with a capital letter, or one all in capitals. */
    private boolean isOwn(String word, Set<String> own) {

        if (own.contains(word))
            return true;

        String lowerFirst = word.substring(0, 1).toLowerCase(language.locale()) + word.substring(1);
        if (own.contains(lowerFirst))
            return true;

        String upper = word.toUpperCase(language.locale());
        return word.equals(upper) && own.stream().anyMatch(each -> each.toUpperCase(language.locale()).equals(upper));
    }

    private static String straight(String word) {

        return word.replace(CURLY_APOSTROPHE, APOSTROPHE);
    }

    private static Set<String> straightened(Collection<String> words) {

        return words.stream().map(SpellChecker::straight).collect(Collectors.toUnmodifiableSet());
    }

    private static InputStream resource(String name) throws IOException {

        InputStream in = SpellChecker.class.getResourceAsStream(name);
        if (in == null)
            throw new IOException("The app has no " + name + " among its spelling dictionaries");

        return in;
    }
}
