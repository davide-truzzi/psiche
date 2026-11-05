package it.infog.psiche.spelling;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EditDistanceTest {

    @Test
    void theSameWordIsNoEditAway() {

        assertThat(EditDistance.between("montenevoso", "montenevoso", 2)).isZero();
    }

    @Test
    void aLetterAddedRemovedOrReplacedIsOneEdit() {

        assertThat(EditDistance.between("montenevso", "montenevoso", 2)).isEqualTo(1);
        assertThat(EditDistance.between("montenevooso", "montenevoso", 2)).isEqualTo(1);
        assertThat(EditDistance.between("montenevaso", "montenevoso", 2)).isEqualTo(1);
    }

    @Test
    void twoLettersSwappedAreOneEdit() {

        assertThat(EditDistance.between("montenveoso", "montenevoso", 2)).isEqualTo(1);
    }

    @Test
    void wordsFurtherApartThanAskedAreJustTooFar() {

        assertThat(EditDistance.between("montagna", "montenevoso", 2)).isEqualTo(3);
        assertThat(EditDistance.between("abcd", "wxyz", 2)).isEqualTo(3);
        assertThat(EditDistance.between("kitten", "sitting", 3)).isEqualTo(3);
    }

    @Test
    void anEmptyWordIsAsFarAsTheOthersLength() {

        assertThat(EditDistance.between("", "ab", 2)).isEqualTo(2);
        assertThat(EditDistance.between("", "abc", 2)).isEqualTo(3);
    }
}
