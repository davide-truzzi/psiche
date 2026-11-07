package it.infog.psiche.git;

import it.infog.psiche.git.model.RemoteAddress;
import it.infog.psiche.git.model.enums.AddressProblem;
import it.infog.psiche.git.model.enums.RemoteKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteAddressesTest {

    @ParameterizedTest
    @ValueSource(strings = {"https://github.com/davide/alba.git", "HTTPS://gitlab.com/davide/alba",
            "  https://git.example.org:8443/books/alba.git  "})
    void anHttpsAddressIsAccepted(String typed) {

        assertThat(RemoteAddresses.problem(typed)).isEmpty();
        assertThat(RemoteAddresses.read(typed)).isEqualTo(new RemoteAddress(RemoteKind.HTTPS, typed.strip()));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "http://github.com/davide/alba.git | NOT_SECURE",
            "git://github.com/davide/alba.git | GIT_PROTOCOL",
            "ssh://git@github.com/davide/alba.git | SSH",
            "git@github.com:davide/alba.git | SSH",
            "github.com:davide/alba.git | SSH",
            "https://davide:ghp_secret@github.com/davide/alba.git | CREDENTIALS_IN_ADDRESS",
            "https://davide@github.com/davide/alba.git | CREDENTIALS_IN_ADDRESS",
            "https:///davide/alba.git | UNKNOWN",
            "ftp://example.org/alba | UNKNOWN",
            "books/alba.git | UNKNOWN",
            "alba | UNKNOWN"})
    void otherAddressesAreRefusedSayingWhy(String typed, AddressProblem problem) {

        assertThat(RemoteAddresses.problem(typed)).contains(problem);
        assertThatThrownBy(() -> RemoteAddresses.read(typed)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(problem.message());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void nothingTypedIsBlank(String typed) {

        assertThat(RemoteAddresses.problem(typed)).contains(AddressProblem.BLANK);
        assertThat(RemoteAddresses.problem(null)).contains(AddressProblem.BLANK);
    }

    @Test
    void aFullPathIsAFolder() {

        assertThat(RemoteAddresses.read(" /media/davide/USB/Backup/ "))
                .isEqualTo(new RemoteAddress(RemoteKind.FOLDER, "/media/davide/USB/Backup"));
    }

    @Test
    void aFileAddressIsAFolder() {

        assertThat(RemoteAddresses.read("file:///media/davide/USB/Backup"))
                .isEqualTo(new RemoteAddress(RemoteKind.FOLDER, "/media/davide/USB/Backup"));
    }

    @Test
    void aSavedAddressIsHttpsOrAFolder() {

        assertThat(RemoteAddresses.kind("https://github.com/davide/alba.git")).isEqualTo(RemoteKind.HTTPS);
        assertThat(RemoteAddresses.kind("/media/davide/USB/PsicheRepository/alba.git")).isEqualTo(RemoteKind.FOLDER);
    }
}
