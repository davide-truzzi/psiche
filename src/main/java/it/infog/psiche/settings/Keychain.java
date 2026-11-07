package it.infog.psiche.settings;

import it.infog.psiche.common.SafeFiles;
import it.infog.psiche.settings.model.Credentials;
import it.infog.psiche.settings.model.SettingsFolder;

import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.util.Arrays;
import java.util.Optional;

/**
 * Psiche's own keychain: the credentials of each book's remote repository, kept in {@value #FILE} in the
 * {@link SettingsFolder}, a PKCS12 file locked by a master password the author chooses. The same on every system,
 * with no native code: a token is never kept in the book, where a Psiche project would carry it along.
 * <p>
 * The keychain is created with the first token saved, then unlocked once a session, when a token is first needed;
 * the master password stays in memory until {@link #lock}, when the app quits. A forgotten one cannot be recovered:
 * {@link #reset} deletes every token, which the author can make again on the host.
 * <p>
 * Each book has one entry, named after its identifier, so moving or renaming its folder keeps it. Every read and
 * change reads the file afresh, so two windows of the app do not undo each other's changes, and every change writes
 * it whole to a temporary file, readable by its owner only, that then takes the file's place.
 */
public final class Keychain {

    static final String FILE = "credentials.p12";

    private static final String TYPE = "PKCS12";
    private static final String PROTECTION = "PBEWithHmacSHA256AndAES_256";
    // The derivations of the key from the master password, as OWASP advises for PBKDF2 with HMAC-SHA256.
    static final int ITERATIONS = 600_000;
    // The JDK takes the derivations of the file's integrity check, which also tells a wrong password, only from this
    // property, read as each file is written; its default, 10,000, would make guessing the password from the file
    // sixty times cheaper than from the entries. So it is set to the entries' own before each write.
    static final String MAC_ITERATIONS_PROPERTY = "keystore.pkcs12.macIterationCount";
    private static final int SALT_LENGTH = 16;
    // PKCS12 keeps a secret as a key of a named algorithm; the bytes inside are the credentials, not an AES key.
    private static final String ENTRY_ALGORITHM = "AES";

    private final Path file;
    private final int iterations;
    private final SecureRandom random = new SecureRandom();
    // Null while locked.
    private char[] password;

    public Keychain(SettingsFolder folder) {

        this(folder, ITERATIONS);
    }

    /** A keychain whose key derivations are {@code iterations}, fewer than the app's, for tests that need speed. */
    Keychain(SettingsFolder folder, int iterations) {

        this.file = folder.path().resolve(FILE);
        this.iterations = iterations;
    }

    /** True if the keychain has been created, so it is unlocked with its master password rather than created. */
    public synchronized boolean exists() {

        return Files.exists(file);
    }

    /** True if the master password has been given in this session. */
    public synchronized boolean unlocked() {

        return password != null;
    }

    /**
     * Creates an empty keychain locked by {@code masterPassword}, and leaves it unlocked. The caller keeps the array
     * and may clear it.
     *
     * @throws IllegalStateException if the keychain exists already
     */
    public synchronized void create(char[] masterPassword) {

        if (exists())
            throw new IllegalStateException("The keychain exists already: " + file);

        char[] chosen = masterPassword.clone();
        write(empty(chosen), chosen);
        password = chosen;
    }

    /**
     * Unlocks the keychain with {@code masterPassword} if it is the right one. The caller keeps the array and may
     * clear it.
     *
     * @return false if the password is wrong
     * @throws UncheckedIOException if the file cannot be read or is damaged
     */
    public synchronized boolean unlock(char[] masterPassword) {

        char[] given = masterPassword.clone();
        if (read(given).isEmpty()) {
            Arrays.fill(given, '\0');
            return false;
        }

        lock();
        password = given;
        return true;
    }

    /** Forgets the master password, which the next token needed asks again. */
    public synchronized void lock() {

        if (password != null)
            Arrays.fill(password, '\0');

        password = null;
    }

    /**
     * The credentials saved for {@code book}; empty if there are none.
     *
     * @param book the book's identifier
     * @throws IllegalStateException if the keychain is locked
     */
    public synchronized Optional<Credentials> credentials(String book) {

        KeyStore store = current();
        try {
            KeyStore.Entry entry = store.getEntry(alias(book), new KeyStore.PasswordProtection(password));
            return entry instanceof KeyStore.SecretKeyEntry secret
                    ? Optional.of(decoded(secret.getSecretKey().getEncoded()))
                    : Optional.empty();
        } catch (GeneralSecurityException e) {
            throw failure("Cannot read the keychain's entry for " + book, e);
        }
    }

    /**
     * Saves {@code credentials} for {@code book}, in place of any it had.
     *
     * @throws IllegalStateException if the keychain is locked
     */
    public synchronized void save(String book, Credentials credentials) {

        KeyStore store = current();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        SecretKeySpec secret = new SecretKeySpec(encoded(credentials), ENTRY_ALGORITHM);
        try {
            store.setEntry(alias(book), new KeyStore.SecretKeyEntry(secret),
                    new KeyStore.PasswordProtection(password, PROTECTION, new PBEParameterSpec(salt, iterations)));
        } catch (GeneralSecurityException e) {
            throw failure("Cannot keep the credentials of " + book, e);
        }

        write(store, password);
    }

    /**
     * Deletes the credentials of {@code book}, if it has any.
     *
     * @throws IllegalStateException if the keychain is locked
     */
    public synchronized void remove(String book) {

        KeyStore store = current();
        try {
            if (!store.containsAlias(alias(book)))
                return;

            store.deleteEntry(alias(book));
        } catch (GeneralSecurityException e) {
            throw failure("Cannot delete the credentials of " + book, e);
        }

        write(store, password);
    }

    /** Deletes the keychain with every token in it, for a forgotten master password, and locks it. */
    public synchronized void reset() {

        lock();
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete the keychain " + file, e);
        }
    }

    /** The keychain as saved, read with the master password, or an empty one if another window reset it. */
    private KeyStore current() {

        if (password == null)
            throw new IllegalStateException("The keychain is locked");

        if (!exists())
            return empty(password);

        Optional<KeyStore> store = read(password);
        if (store.isPresent())
            return store.get();

        // Another window reset the keychain and made a new one meanwhile.
        lock();
        throw new UncheckedIOException(new IOException("The keychain was changed in another window of Psiche; "
                + "type its master password again"));
    }

    /** The file read with {@code masterPassword}; empty if it is the wrong one. */
    private Optional<KeyStore> read(char[] masterPassword) {

        try (InputStream in = Files.newInputStream(file)) {
            KeyStore store = KeyStore.getInstance(TYPE);
            store.load(in, masterPassword);
            return Optional.of(store);
        } catch (IOException e) {
            if (e.getCause() instanceof UnrecoverableKeyException)
                return Optional.empty();

            throw new UncheckedIOException("Cannot read the keychain " + file, e);
        } catch (GeneralSecurityException e) {
            throw failure("Cannot read the keychain " + file, e);
        }
    }

    /** Writes {@code store} whole in place of the file, through a temporary file only its owner can read. */
    private void write(KeyStore store, char[] masterPassword) {

        try {
            Files.createDirectories(file.getParent());
            // Made readable and writable by its owner only, on systems with POSIX permissions.
            Path temporary = SafeFiles.temporaryBeside(file);
            try {
                System.setProperty(MAC_ITERATIONS_PROPERTY, String.valueOf(iterations));
                try (OutputStream out = Files.newOutputStream(temporary)) {
                    store.store(out, masterPassword);
                }
                SafeFiles.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save the keychain " + file, e);
        } catch (GeneralSecurityException e) {
            throw failure("Cannot save the keychain " + file, e);
        }
    }

    /** A failure of the JDK's keystore, reported as the file's, as the author can do nothing else about either. */
    private static UncheckedIOException failure(String message, GeneralSecurityException cause) {

        return new UncheckedIOException(new IOException(message, cause));
    }

    private static KeyStore empty(char[] masterPassword) {

        try {
            KeyStore store = KeyStore.getInstance(TYPE);
            store.load(null, masterPassword);
            return store;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("The JDK has no " + TYPE + " keystore", e);
        }
    }

    /** The entry's name for {@code book}, which PKCS12 compares in lower case. */
    private static String alias(String book) {

        if (book == null || book.isBlank())
            throw new IllegalArgumentException("A book without an identifier has no place in the keychain");

        return book.strip();
    }

    private static byte[] encoded(Credentials credentials) {

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(credentials.username());
            out.writeUTF(credentials.token());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return bytes.toByteArray();
    }

    private static Credentials decoded(byte[] encoded) {

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded))) {
            return new Credentials(in.readUTF(), in.readUTF());
        } catch (IOException e) {
            throw new UncheckedIOException("A keychain entry that holds no credentials", e);
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }
}
