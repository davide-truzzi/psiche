package it.infog.psiche.spelling;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class EditDistanceTest {

    /** {@code limit} is how far to look: a word further away is {@code limit + 1}, "just too far". */
    @ParameterizedTest
    @MethodSource("words")
    void countsTheEditsBetweenTwoWords(String word, String other, int limit, int expected) {

        assertThat(EditDistance.between(word, other, limit)).isEqualTo(expected);
    }

    private static Stream<Arguments> words() {

        return Stream.of(
                argumentSet("the same word is no edit away", "montenevoso", "montenevoso", 2, 0),
                argumentSet("a letter removed is one edit", "montenevso", "montenevoso", 2, 1),
                argumentSet("a letter added is one edit", "montenevooso", "montenevoso", 2, 1),
                argumentSet("a letter replaced is one edit", "montenevaso", "montenevoso", 2, 1),
                argumentSet("two letters swapped are one edit", "montenveoso", "montenevoso", 2, 1),
                argumentSet("a word further apart than asked is just too far", "montagna", "montenevoso", 2, 3),
                argumentSet("words with nothing in common are just too far", "abcd", "wxyz", 2, 3),
                argumentSet("three edits found when three are asked", "kitten", "sitting", 3, 3),
                argumentSet("an empty word is as far as the other's length", "", "ab", 2, 2),
                argumentSet("an empty word, past the limit, is just too far", "", "abc", 2, 3));
    }
}
