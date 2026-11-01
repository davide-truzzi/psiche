package it.infog.psiche.ui.git;

import it.infog.psiche.git.BookRepository;
import it.infog.psiche.git.model.Commit;
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

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

import static it.infog.psiche.ui.common.Controls.button;

/** Commit message, Commit and Push for the open book's repository. Disabled while there is none. */
public final class GitControls extends HBox {

    // One thread so Git commands run in the order the author clicked them, off the FX thread.
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("git").daemon().factory());

    private final TextField commitMessage = new TextField();
    private final BooleanProperty available = new SimpleBooleanProperty();
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper();
    private final BooleanSupplier confirmCommit;
    private final Runnable afterCommit;
    private final StatusBar statusBar;
    private final ErrorReporter errors;

    private BookRepository repository;

    /**
     * @param confirmCommit runs before Commit, to settle unsaved chapters; returning false cancels it
     * @param afterCommit   runs on the FX thread once a commit has been recorded
     */
    public GitControls(BooleanSupplier confirmCommit, Runnable afterCommit, StatusBar statusBar, ErrorReporter errors) {
        this.confirmCommit = confirmCommit;
        this.afterCommit = afterCommit;
        this.statusBar = statusBar;
        this.errors = errors;

        BooleanBinding disabled = available.not().or(busy);
        commitMessage.setPromptText("Describe this revision");
        commitMessage.getStyleClass().add("commit-message");
        commitMessage.setOnAction(event -> commit());
        commitMessage.disableProperty().bind(disabled);

        Button commit = button("Commit", this::commit);
        commit.getStyleClass().add("primary");
        commit.disableProperty().bind(disabled.or(
                Bindings.createBooleanBinding(() -> commitMessage.getText().isBlank(), commitMessage.textProperty())));

        Button push = button("Push", this::push);
        push.disableProperty().bind(disabled);

        getChildren().addAll(commitMessage, commit, push);
        getStyleClass().add("git-controls");
    }

    /** @param repository the open book's repository, or {@code null} to disable the controls */
    public void setRepository(BookRepository repository) {
        this.repository = repository;
        available.set(repository != null);
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
     * Keeps {@code pattern} out of Git, as {@link BookRepository#ignore} does, on the Git thread; nothing for a book
     * without a repository.
     */
    public void ignore(String pattern, String message) {

        if (repository == null)
            return;

        run("Commit", repo -> repo.ignore(pattern, message), changed -> {
            statusBar.info(changed ? "Committed " + message : "Notes are kept out of Git");
            if (changed)
                afterCommit.run();
        });
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

    private void push() {
        run("Push", BookRepository::push, branch -> statusBar.info("Pushed %s to origin".formatted(branch)));
    }

    private <T> void run(String action, Function<BookRepository, T> command, Consumer<T> onSuccess) {

        // Read the field here on the FX thread; the task runs later on the Git thread, when it may have changed.
        BookRepository target = repository;
        Task<T> task = new Task<>() {
            @Override
            protected T call() {
                return command.apply(target);
            }
        };
        task.setOnSucceeded(event -> {
            busy.set(false);
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            busy.set(false);
            errors.report(action + " failed", task.getException());
        });
        busy.set(true);
        statusBar.info(action + "…");
        executor.execute(task);
    }

    /** Stops the Git thread, interrupting a running command. */
    public void shutdown() {
        executor.shutdownNow();
    }
}
