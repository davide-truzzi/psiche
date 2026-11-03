package it.infog.psiche.ui.chapters;

import it.infog.psiche.spelling.Dictionaries;
import it.infog.psiche.spelling.SpellChecker;
import it.infog.psiche.spelling.model.SpellingLanguage;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Which dictionary checks the manuscript: the one of the open book's language, while spell checking is on in
 * Preferences; none with no book, no dictionary for its language, or checking off. A dictionary is read on a thread of
 * its own the first time a book needs it, which takes a moment, and kept for the rest of the run; until it is read,
 * the manuscript is not checked. Used on the FX thread.
 */
final class Spelling {

    private static final Logger log = LoggerFactory.getLogger(Spelling.class);

    private final ExecutorService reader =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("spelling").daemon().factory());
    private final Map<SpellingLanguage, SpellChecker> read = new HashMap<>();
    private final Set<SpellingLanguage> reading = new HashSet<>();
    private final Consumer<Optional<SpellChecker>> use;
    private boolean on = true;
    // The open book's language, as a BCP 47 tag; none while no book is open.
    private String bookLanguage;

    /** @param use given the dictionary to check the manuscript with, or none, each time that changes */
    Spelling(Consumer<Optional<SpellChecker>> use) {

        this.use = use;
    }

    /** Turns spell checking on or off, as Preferences asks. */
    void setOn(boolean on) {

        this.on = on;
        choose();
    }

    /** Follows the open book's language, a BCP 47 tag, or none with {@code null} while no book is open. */
    void setBookLanguage(String tag) {

        bookLanguage = tag;
        choose();
    }

    /** Hands over the dictionary the manuscript needs now, reading it first if it is not read yet. */
    private void choose() {

        Optional<SpellingLanguage> wanted = wanted();
        if (wanted.isEmpty() || read.containsKey(wanted.get())) {
            use.accept(wanted.map(read::get));
            return;
        }

        // Until it is read, the words are not checked: no mistakes of another language are left showing.
        use.accept(Optional.empty());
        SpellingLanguage language = wanted.get();
        if (!reading.add(language))
            return;

        CompletableFuture.supplyAsync(() -> SpellChecker.load(language), reader)
                .whenComplete((checker, failure) -> Platform.runLater(() -> {
                    reading.remove(language);
                    if (failure != null) {
                        log.warn("Cannot read the {} spelling dictionary", language.tag(), failure);
                        return;
                    }
                    read.put(language, checker);
                    // The author may have turned checking off, or opened a book in another language, meanwhile.
                    if (wanted().filter(language::equals).isPresent())
                        use.accept(Optional.of(checker));
                }));
    }

    private Optional<SpellingLanguage> wanted() {

        return on && bookLanguage != null ? Dictionaries.forBook(bookLanguage) : Optional.empty();
    }
}
