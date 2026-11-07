package it.infog.psiche.ui.git;

import it.infog.psiche.git.BookRepository;
import it.infog.psiche.git.RemoteAddresses;
import it.infog.psiche.git.exceptions.GitCommandException;
import it.infog.psiche.git.model.Commit;
import it.infog.psiche.git.model.RemoteAddress;
import it.infog.psiche.git.model.enums.RemoteKind;
import it.infog.psiche.settings.Keychain;
import it.infog.psiche.settings.model.Credentials;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

import static it.infog.psiche.ui.common.Controls.button;

/**
 * Commit message, Commit and Synchronize for the open book's repository, disabled while there is none; Synchronize
 * shows only for a book with a remote. Every Git command runs here, on one thread, connecting and removing the
 * book's remote and Book properties' Test among them.
 */
public final class GitControls extends HBox {

    private static final Logger log = LoggerFactory.getLogger(GitControls.class);

    // One thread so Git commands run in the order the author clicked them, off the FX thread.
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("git").daemon().factory());

    private final TextField commitMessage = new TextField();
    private final BooleanProperty available = new SimpleBooleanProperty();
    private final BooleanProperty remoteConnected = new SimpleBooleanProperty();
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper();
    private final BooleanSupplier confirmCommit;
    private final Runnable afterCommit;
    private final StatusBar statusBar;
    private final ErrorReporter errors;
    private final Keychain keychain;

    private BookRepository repository;
    // The book's identifier, which names its entry in the keychain.
    private String book = "";

    /**
     * @param confirmCommit runs before Commit, to settle unsaved chapters; returning false cancels it
     * @param afterCommit   runs on the FX thread once a commit has been recorded
     * @param keychain      where the tokens of the books' remotes are kept
     */
    public GitControls(BooleanSupplier confirmCommit, Runnable afterCommit, StatusBar statusBar, ErrorReporter errors,
                       Keychain keychain) {

        this.confirmCommit = confirmCommit;
        this.afterCommit = afterCommit;
        this.statusBar = statusBar;
        this.errors = errors;
        this.keychain = keychain;

        BooleanBinding disabled = available.not().or(busy);
        commitMessage.setPromptText("Describe this revision");
        commitMessage.getStyleClass().add("commit-message");
        commitMessage.setOnAction(event -> commit());
        commitMessage.disableProperty().bind(disabled);

        Button commit = button("Commit", this::commit);
        commit.getStyleClass().add("primary");
        commit.disableProperty().bind(disabled.or(
                Bindings.createBooleanBinding(() -> commitMessage.getText().isBlank(), commitMessage.textProperty())));

        // Hidden, not only disabled, for a book without a remote: most authors never need one.
        Button synchronize = button("Synchronize", this::synchronize);
        synchronize.disableProperty().bind(disabled);
        synchronize.visibleProperty().bind(remoteConnected);
        synchronize.managedProperty().bind(remoteConnected);

        getChildren().addAll(commitMessage, commit, synchronize);
        getStyleClass().add("git-controls");
    }

    /**
     * @param repository the open book's repository, or {@code null} to disable the controls
     * @param book       the open book's identifier
     */
    public void setRepository(BookRepository repository, String book) {

        this.repository = repository;
        this.book = book;
        available.set(repository != null);
        remoteConnected.set(repository != null && repository.remote().address().isPresent());
    }

    /** True while a Git command is running. */
    public ReadOnlyBooleanProperty busyProperty() {
        return busy.getReadOnlyProperty();
    }

    /**
     * Records a change the workspace has just made to {@code files}, such as a renamed chapter, as a commit of its
     * own with {@code message}. The author's commit message is left as it is.
     */
    public void commitChange(String message, List<Path> files) {
        run("Commit", repo -> repo.commitOnly(message, files), this::committed);
    }

    /**
     * Keeps {@code pattern} out of Git, as {@link BookRepository#ignore} does, on the Git thread, committing it with
     * {@code message}; nothing for a book without a repository.
     *
     * @param alreadyKept what the status bar says if {@code pattern} was out of Git already
     */
    public void ignore(String pattern, String message, String alreadyKept) {

        if (repository == null)
            return;

        run("Commit", repo -> repo.ignore(pattern, message), changed -> {
            statusBar.info(changed ? "Committed " + message : alreadyKept);
            if (changed)
                afterCommit.run();
        });
    }

    /**
     * Keeps {@code pattern} out of Git as {@link #ignore} does, but does nothing, not even a word in the status bar, if
     * it already is, as the author did not ask for it.
     */
    public void ignoreQuietly(String pattern, String message) {

        if (repository != null && !repository.ignores(pattern))
            ignore(pattern, message, "");
    }

    /**
     * Asks {@code repository} {@code question} on the Git thread, after the commands clicked before it, and hands the
     * answer to {@code answer} on the FX thread. Quietly: the controls stay as they are, and a question that cannot
     * be answered is only logged, as nothing was asked by the author.
     */
    public <T> void read(BookRepository repository, Function<BookRepository, T> question, Consumer<T> answer) {

        submit(repository, question, answer, error -> log.warn("Cannot read the state of the book", error));
    }

    private void commit() {

        if (!confirmCommit.getAsBoolean())
            return;

        run("Commit", repo -> repo.commit(commitMessage.getText()), commit -> {
            commitMessage.clear();
            committed(commit);
        });
    }

    private void committed(Commit commit) {
        statusBar.info("Committed %s  %s".formatted(commit.shortId(), commit.message()));
        afterCommit.run();
    }

    /**
     * Makes {@code address} the book's remote, saving {@code credentials}, if any, as the book's in the keychain,
     * which the caller has unlocked.
     */
    public void connectRemote(RemoteAddress address, Optional<Credentials> credentials) {

        String identifier = book;
        run("Connect the remote", repo -> {
            credentials.ifPresent(given -> keychain.save(identifier, given));
            return repo.remote().connect(address);
        }, saved -> {
            remoteConnected.set(true);
            statusBar.info("Connected to the remote " + saved);
        });
    }

    /** Disconnects the book from its remote, and deletes its token if the keychain is unlocked, as it is to confirm. */
    public void removeRemote() {

        String identifier = book;
        run("Remove the remote", repo -> {
            repo.remote().remove();
            if (keychain.unlocked())
                keychain.remove(identifier);
            return identifier;
        }, removed -> {
            remoteConnected.set(false);
            statusBar.info("The remote is removed");
        });
    }

    /**
     * The credentials saved for the book's remote, read on the Git thread: none for a folder.
     *
     * @throws GitCommandException if an HTTPS remote has no token saved
     */
    public Optional<Credentials> savedCredentials(RemoteKind kind) {

        if (kind == RemoteKind.FOLDER)
            return Optional.empty();

        return Optional.of(keychain.credentials(book).orElseThrow(() -> new GitCommandException(
                "No token is saved for this book: type it in Book properties, under Remote repository.")));
    }

    /**
     * Asks {@code question} of the open book's repository on the Git thread, after the commands clicked before it,
     * and hands the answer to {@code answer}, or what went wrong to {@code failure}, on the FX thread.
     */
    public <T> void ask(Function<BookRepository, T> question, Consumer<T> answer, Consumer<Throwable> failure) {

        submit(repository, question, answer, failure);
    }

    /** Sends the book's commits to its remote, after unlocking the keychain for an HTTPS one. */
    private void synchronize() {

        RemoteKind kind = repository.remote().address().map(RemoteAddresses::kind).orElse(RemoteKind.FOLDER);
        if (kind == RemoteKind.HTTPS && !KeychainDialogs.unlocked(getScene().getWindow(), keychain))
            return;

        // It only sends, until it learns to bring in the remote's revisions and merge them.
        run("Synchronize", repo -> repo.push(savedCredentials(kind)),
                branch -> statusBar.info("Synchronized with the remote"));
    }

    /** Runs a command the author asked for, saying so in the status bar, and busy until it ends. */
    private <T> void run(String action, Function<BookRepository, T> command, Consumer<T> onSuccess) {

        busy.set(true);
        statusBar.info(action + "…");

        // Read the field here on the FX thread; the task runs later on the Git thread, when it may have changed.
        submit(repository, command, result -> {
            busy.set(false);
            onSuccess.accept(result);
        }, error -> {
            busy.set(false);
            errors.report(action + " failed", error);
        });
    }

    /**
     * Applies {@code command} to {@code target} on the Git thread, after what was submitted before, and hands its
     * result to {@code onSuccess}, or what went wrong to {@code onFailure}, on the FX thread.
     */
    private <T> void submit(BookRepository target, Function<BookRepository, T> command, Consumer<T> onSuccess,
                            Consumer<Throwable> onFailure) {

        Task<T> task = new Task<>() {
            @Override
            protected T call() {
                return command.apply(target);
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onFailure.accept(task.getException()));
        executor.execute(task);
    }

    /** Stops the Git thread, interrupting a running command. */
    public void shutdown() {
        executor.shutdownNow();
    }
}
