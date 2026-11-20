package it.infog.psiche.settings;

import it.infog.psiche.settings.model.Credentials;
import it.infog.psiche.settings.model.SettingsFolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class KeychainTest {

    private static final String ALBA = "urn:uuid:7d1f0a52-3b8e-4c43-9a51-0f3a8d5e2b10";
    private static final String SERA = "urn:uuid:c2a9e4d1-58f6-4b7a-8e0c-6d2b1f9a7c35";
    private static final Credentials DAVIDE = new Credentials("davide", "ghp_secret0123456789");
    // Few derivations, so the tests run fast; one test checks the app's own count.
    private static final int FAST = 1_000;

    @TempDir
    Path tempDir;

    @Test
    void theAppsKeychainDerivesTheKeysAndTheIntegrityCheckAsOwaspAdvises() {

        Keychain created = new Keychain(new SettingsFolder(tempDir, false));
        created.create(password("master"));
        created.save(ALBA, DAVIDE);
        assertThat(System.getProperty(Keychain.MAC_ITERATIONS_PROPERTY))
                .isEqualTo(String.valueOf(Keychain.ITERATIONS));

        Keychain next = new Keychain(new SettingsFolder(tempDir, false));
        assertThat(next.unlock(password("master"))).isTrue();
        assertThat(next.credentials(ALBA)).contains(DAVIDE);
    }

    @Test
    void aKeychainNeverCreatedDoesNotExistAndIsLocked() {

        Keychain keychain = keychain();

        assertThat(keychain.exists()).isFalse();
        assertThat(keychain.unlocked()).isFalse();
    }

    @Test
    void aNewKeychainIsUnlockedAndEmpty() {

        Keychain keychain = keychain();

        keychain.create(password("master"));

        assertThat(keychain.exists()).isTrue();
        assertThat(keychain.unlocked()).isTrue();
        assertThat(keychain.credentials(ALBA)).isEmpty();
    }

    @Test
    void creatingItTwiceIsRefused() {

        keychain().create(password("master"));

        assertThatThrownBy(() -> keychain().create(password("other"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void credentialsSavedAreReadInTheNextSessionWithTheMasterPassword() {

        Keychain first = keychain();
        first.create(password("master"));
        first.save(ALBA, DAVIDE);
        first.lock();

        Keychain next = keychain();

        assertThat(next.unlock(password("master"))).isTrue();
        assertThat(next.credentials(ALBA)).contains(DAVIDE);
    }

    @Test
    void aWrongPasswordLeavesItLocked() {

        keychain().create(password("master"));
        Keychain keychain = keychain();

        assertThat(keychain.unlock(password("Master"))).isFalse();
        assertThat(keychain.unlocked()).isFalse();
    }

    @Test
    void aLockedKeychainRefusesToReadOrChange() {

        Keychain keychain = keychain();
        keychain.create(password("master"));
        keychain.lock();

        assertThatThrownBy(() -> keychain.credentials(ALBA)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> keychain.save(ALBA, DAVIDE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> keychain.remove(ALBA)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theCallerMayClearThePasswordItGave() {

        Keychain keychain = keychain();
        char[] typed = password("master");
        keychain.create(typed);
        Arrays.fill(typed, '\0');

        keychain.save(ALBA, DAVIDE);

        assertThat(keychain().unlock(password("master"))).isTrue();
    }

    @Test
    void eachBookHasItsOwnEntry() {

        Keychain keychain = unlocked();
        Credentials other = new Credentials("davide-t", "glpat-another");

        keychain.save(ALBA, DAVIDE);
        keychain.save(SERA, other);

        assertThat(keychain.credentials(ALBA)).contains(DAVIDE);
        assertThat(keychain.credentials(SERA)).contains(other);
    }

    @Test
    void savingAgainReplacesTheBooksCredentials() {

        Keychain keychain = unlocked();
        Credentials renewed = new Credentials("davide", "ghp_renewed");

        keychain.save(ALBA, DAVIDE);
        keychain.save(ALBA, renewed);

        assertThat(keychain.credentials(ALBA)).contains(renewed);
    }

    @Test
    void removingDeletesOnlyThatBooksCredentials() {

        Keychain keychain = unlocked();
        keychain.save(ALBA, DAVIDE);
        keychain.save(SERA, DAVIDE);

        keychain.remove(ALBA);
        keychain.remove(ALBA);

        assertThat(keychain.credentials(ALBA)).isEmpty();
        assertThat(keychain.credentials(SERA)).contains(DAVIDE);
    }

    @Test
    void resetDeletesEveryTokenAndLocks() {

        Keychain keychain = unlocked();
        keychain.save(ALBA, DAVIDE);

        keychain.reset();

        assertThat(keychain.exists()).isFalse();
        assertThat(keychain.unlocked()).isFalse();
        keychain.create(password("new"));
        assertThat(keychain.credentials(ALBA)).isEmpty();
    }

    @Test
    void aChangedPasswordKeepsEveryTokenAndLeavesItUnlocked() {

        Keychain keychain = unlocked();
        keychain.save(ALBA, DAVIDE);
        keychain.save(SERA, DAVIDE);

        assertThat(keychain.changePassword(password("master"), password("new master"))).isTrue();

        assertThat(keychain.unlocked()).isTrue();
        assertThat(keychain.credentials(ALBA)).contains(DAVIDE);
        Keychain next = keychain();
        assertThat(next.unlock(password("new master"))).isTrue();
        assertThat(next.credentials(SERA)).contains(DAVIDE);
    }

    @Test
    void theOldPasswordNoLongerUnlocksItsFileNorItsEntries() throws IOException, GeneralSecurityException {

        Keychain keychain = unlocked();
        keychain.save(ALBA, DAVIDE);

        keychain.changePassword(password("master"), password("new master"));

        assertThat(keychain().unlock(password("master"))).isFalse();
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(tempDir.resolve(Keychain.FILE))) {
            store.load(in, password("new master"));
        }
        assertThatThrownBy(() -> store.getEntry(ALBA, new KeyStore.PasswordProtection(password("master"))))
                .isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    void aWrongCurrentPasswordChangesNothing() {

        Keychain keychain = unlocked();
        keychain.save(ALBA, DAVIDE);

        assertThat(keychain.changePassword(password("Master"), password("new master"))).isFalse();

        assertThat(keychain.credentials(ALBA)).contains(DAVIDE);
        assertThat(keychain().unlock(password("master"))).isTrue();
    }

    @Test
    void theCurrentPasswordChangesALockedKeychainToo() {

        unlocked().save(ALBA, DAVIDE);
        Keychain locked = keychain();

        assertThat(locked.changePassword(password("master"), password("new master"))).isTrue();

        assertThat(locked.credentials(ALBA)).contains(DAVIDE);
    }

    @Test
    void anotherWindowStillOnTheOldPasswordIsLocked() {

        Keychain window = unlocked();
        keychain().changePassword(password("master"), password("new master"));

        assertThatThrownBy(() -> window.credentials(ALBA)).isInstanceOf(UncheckedIOException.class);
        assertThat(window.unlocked()).isFalse();
    }

    @Test
    void aKeychainNeverCreatedHasNoPasswordToChange() {

        assertThatThrownBy(() -> keychain().changePassword(password("master"), password("new master")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void changesMadeInAnotherWindowAreKept() {

        Keychain window = unlocked();
        Keychain other = keychain();
        other.unlock(password("master"));

        window.save(ALBA, DAVIDE);
        other.save(SERA, DAVIDE);

        assertThat(window.credentials(ALBA)).contains(DAVIDE);
        assertThat(window.credentials(SERA)).contains(DAVIDE);
    }

    @Test
    void aKeychainRemadeInAnotherWindowLocksThisOne() {

        Keychain window = unlocked();
        Keychain other = keychain();
        other.reset();
        other.create(password("different"));

        assertThatThrownBy(() -> window.credentials(ALBA)).isInstanceOf(UncheckedIOException.class);
        assertThat(window.unlocked()).isFalse();
    }

    @Test
    void aBookWithoutAnIdentifierHasNoEntry() {

        Keychain keychain = unlocked();

        assertThatThrownBy(() -> keychain.save(" ", DAVIDE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDamagedFileCannotBeUnlocked() throws IOException {

        Files.writeString(tempDir.resolve(Keychain.FILE), "not a keychain");

        assertThatThrownBy(() -> keychain().unlock(password("master"))).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void theFileIsReadableByItsOwnerOnly() throws IOException {

        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Keychain keychain = unlocked();
        keychain.save(ALBA, DAVIDE);

        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(tempDir.resolve(Keychain.FILE))))
                .isEqualTo("rw-------");
    }

    @Test
    void theTokenIsNeitherInTheFileNorInTheCredentialsText() throws IOException {

        Keychain keychain = unlocked();
        keychain.save(ALBA, DAVIDE);

        String file = new String(Files.readAllBytes(tempDir.resolve(Keychain.FILE)), StandardCharsets.ISO_8859_1);
        assertThat(file).doesNotContain(DAVIDE.token()).doesNotContain(DAVIDE.username());
        assertThat(DAVIDE.toString()).doesNotContain(DAVIDE.token()).contains(DAVIDE.username());
    }

    @Test
    void noTemporaryFileIsLeftBehind() throws IOException {

        Keychain keychain = unlocked();
        keychain.save(ALBA, DAVIDE);

        try (var files = Files.list(tempDir)) {
            assertThat(files.map(file -> file.getFileName().toString())).containsExactly(Keychain.FILE);
        }
    }

    private Keychain unlocked() {

        Keychain keychain = keychain();
        keychain.create(password("master"));
        return keychain;
    }

    private Keychain keychain() {

        return new Keychain(new SettingsFolder(tempDir, false), FAST);
    }

    private static char[] password(String text) {

        return text.toCharArray();
    }
}
