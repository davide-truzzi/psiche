package it.infog.psiche.spelling;

import it.infog.psiche.spelling.model.SpellingLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DictionariesTest {

    // Where the dictionaries are in the sources, as the build packs them into the app.
    private static final Path FOLDER = Path.of("src/main/resources/it/infog/psiche/spelling");

    @ParameterizedTest
    @CsvSource({
            "en-US, en-US",
            "en-GB, en-US",
            "en, en-US",
            "it-IT, it-IT",
            "it-CH, it-IT",
            "fr-CA, fr-FR",
            "es-MX, es-ES",
            "de-AT, de-DE",
            "pt-BR, pt-BR",
            "pt-PT, pt-PT",
            "pt-AO, pt-BR"})
    void aBookIsCheckedWithItsCountrysDictionaryOrElseTheFirstOfItsLanguage(String book, String dictionary) {

        assertThat(Dictionaries.forBook(book)).map(SpellingLanguage::tag).contains(dictionary);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nl-NL", "ja-JP", ""})
    void aLanguageWithoutADictionaryHasNone(String book) {

        assertThat(Dictionaries.forBook(book)).isEmpty();
    }

    @Test
    void theIndexListsEveryDictionaryFolderAndNothingElse() throws IOException {

        try (Stream<Path> entries = Files.list(FOLDER)) {
            List<String> folders = entries.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .toList();

            assertThat(Dictionaries.bundled())
                    .extracting(SpellingLanguage::tag)
                    .containsExactlyInAnyOrderElementsOf(folders);
        }
    }

    @Test
    void eachDictionaryHasItsRulesItsWordsAndItsLicence() throws IOException {

        for (SpellingLanguage language : Dictionaries.bundled()) {
            Path folder = FOLDER.resolve(language.tag());
            assertThat(FOLDER.resolve(language.affixFile())).isRegularFile();
            assertThat(FOLDER.resolve(language.wordsFile())).isRegularFile();
            try (Stream<Path> files = Files.list(folder)) {
                assertThat(files.map(path -> path.getFileName().toString()))
                        .as("a README or licence in %s", folder)
                        .anyMatch(name -> name.startsWith("README") || name.startsWith("LICENSE"));
            }
        }
    }

    @Test
    void theIndexSkipsNotesAndBlankLines() {

        assertThat(Dictionaries.read(new BufferedReader(new StringReader("# A note\n\n  it-IT  \npt-BR\n"))))
                .extracting(SpellingLanguage::tag)
                .containsExactly("it-IT", "pt-BR");
    }

    @Test
    void aLineThatIsNoLanguageTagIsRefused() {

        assertThatThrownBy(() -> Dictionaries.read(new BufferedReader(new StringReader("it_IT\n"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'it_IT'");
    }
}
