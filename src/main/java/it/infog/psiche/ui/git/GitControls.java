package it.infog.psiche.ui.git;

import it.infog.psiche.git.BookRepository;
import it.infog.psiche.git.GitIgnore;
import it.infog.psiche.git.RemoteAddresses;
import it.infog.psiche.git.exceptions.GitCommandException;
import it.infog.psiche.git.model.Commit;
import it.infog.psiche.git.model.RemoteAddress;
import it.infog.psiche.git.model.SyncReport;
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
 * shows only for a book with a remote, and runs by itself too, as {@link Synchronizer} tells. Every Git command runs
 * here, on one thread, connecting and removing the book's remote and Book properties' Test among them.
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
    private final Synchronizer synchronizer;

    private Runnable afterBookTaken = () -> {};

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
        synchronizer = new Synchronizer(this, keychain, statusBar, errors);

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
        Button synchronize = button("Synchronize", synchronizer::clicked);
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

    /** True while Synchronize runs, which may change the book's files: nothing else may change them meanwhile. */
    public ReadOnlyBooleanProperty synchronizingProperty() {

        return synchronizer.synchronizingProperty();
    }

    /**
     * @param unsavedWork      true if chapters have unsaved changes, which keep Synchronize from running
     * @param afterSynchronize runs on the FX thread once Synchronize is done, to show the book as it left it
     */
    public void setSynchronization(BooleanSupplier unsavedWork, Consumer<SyncReport> afterSynchronize) {

        synchronizer.setSynchronization(unsavedWork, afterSynchronize);
    }

    /**
     * Whether the open book synchronizes by itself: as it opens and closes, and after every commit; see
     * {@link Synchronizer}.
     */
    public void setAutoSync(boolean autoSync) {

        synchronizer.setAutoSync(autoSync);
    }

    /**
     * Checks, as the book opens, whether its remote has newer revisions, after the commands submitted before; nothing
     * for a book without a remote. See {@link Synchronizer#checkAtOpen}.
     */
    public void checkRemote() {

        synchronizer.checkAtOpen();
    }

    /**
     * Readies the open book to close, once its unsaved chapters are settled: synchronizes it if it does so by itself,
     * then runs {@code then}; nothing runs if the author cancels. See {@link Synchronizer#leave}.
     */
    public void leave(Runnable then) {

        synchronizer.leave(then);
    }

    /**
     * Records a change the workspace has just made to {@code files}, such as a renamed chapter, as a commit of its
     * own with {@code message}. The author's commit message is left as it is.
     */
    public void commitChange(String message, List<Path> files) {

        run("Commit", repo -> repo.commitOnly(message, files), commit -> {
            committed(commit);
            synchronizer.afterCommit();
        });
    }

    /**
     * Keeps {@code pattern} out of Git, as {@link GitIgnore#add} does, on the Git thread, committing it with
     * {@code message}; nothing for a book without a repository.
     *
     * @param alreadyKept what the status bar says if {@code pattern} was out of Git already
     */
    public void ignore(String pattern, String message, String alreadyKept) {

        if (repository == null)
            return;

        run("Commit", repo -> repo.gitignore().add(pattern, message), changed -> {
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

        if (repository != null && !repository.gitignore().has(pattern))
            ignore(pattern, message, "");
    }

    /**
     * Brings into Git the files of {@code folder} that {@code dropped} kept out, as {@link GitIgnore#track} does,
     * on the Git thread; nothing, not even a word in the status bar, if done already, as the author did not ask for it.
     */
    public void track(String dropped, String keptOut, Path folder, String message) {

        if (repository == null || repository.gitignore().has(keptOut) && !repository.gitignore().has(dropped))
            return;

        run("Commit", repo -> repo.gitignore().track(dropped, keptOut, folder, message), changed -> {
            statusBar.info(changed ? "Committed " + message : "");
            if (changed)
                afterCommit.run();
        });
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

        commit(commitMessage.getText(), synchronizer::afterCommit, () -> {});
    }

    private void committed(Commit commit) {

        statusBar.info("Committed %s  %s".formatted(commit.shortId(), commit.message()));
        afterCommit.run();
    }

    /** Runs {@code action} on the FX thread once an empty book has taken the remote's, to show the book it now is. */
    public void setOnBookTaken(Runnable action) {

        afterBookTaken = action;
    }

    /**
     * Makes {@code address} the book's remote, saving {@code credentials}, if any, as the book's in the keychain,
     * which the caller has unlocked.
     *
     * @param takesBook true to put the remote's book in place of this empty one, as {@link BookRepository#adopt}
     *                  does; the credentials are then saved under the remote book's identifier, the one it takes
     */
    public void connectRemote(RemoteAddress address, Optional<Credentials> credentials, boolean takesBook) {

        String identifier = book;
        run("Connect the remote", repo -> {
            String saved = repo.remote().connect(address);
            String owner = takesBook ? repo.adopt(credentials) : identifier;
            credentials.ifPresent(given -> keychain.save(owner.isBlank() ? identifier : owner, given));
            return saved;
        }, saved -> {
            remoteConnected.set(true);
            if (takesBook)
                afterBookTaken.run();
            statusBar.info((takesBook ? "Brought in the book from the remote " : "Connected to the remote ") + saved);
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

    /** Puts the cursor in the field beside Commit, for the author to describe a revision and commit it. */
    public void focusCommitMessage() {

        commitMessage.requestFocus();
    }

    /** What is typed beside Commit. */
    String commitMessage() {

        return commitMessage.getText();
    }

    /**
     * Commits every change in the book with {@code message}, emptying the field beside Commit, then runs {@code then};
     * or {@code failed}, once the author is told what went wrong.
     */
    void commit(String message, Runnable then, Runnable failed) {

        run("Commit", repo -> repo.commit(message), commit -> {
            commitMessage.clear();
            committed(commit);
            then.run();
        }, error -> {
            errors.report("Commit failed", error);
            failed.run();
        });
    }

    /** True if the open book has a remote. */
    boolean hasRemote() {

        return remoteConnected.get();
    }

    /** The open book's remote's kind; a folder for a book without one, which needs no credentials. */
    RemoteKind remoteKind() {

        return repository.remote().address().map(RemoteAddresses::kind).orElse(RemoteKind.FOLDER);
    }

    /** Runs a command the author asked for, saying so in the status bar, and busy until it ends. */
    <T> void run(String action, Function<BookRepository, T> command, Consumer<T> onSuccess) {

        run(action, command, onSuccess, error -> errors.report(action + " failed", error));
    }

    /** As {@link #run(String, Function, Consumer)}, handing what went wrong to {@code onFailure}. */
    <T> void run(String action, Function<BookRepository, T> command, Consumer<T> onSuccess,
                 Consumer<Throwable> onFailure) {

        busy.set(true);
        statusBar.info(action + "…");

        // Read the field here on the FX thread; the task runs later on the Git thread, when it may have changed.
        submit(repository, command, result -> {
            busy.set(false);
            onSuccess.accept(result);
        }, error -> {
            busy.set(false);
            onFailure.accept(error);
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
