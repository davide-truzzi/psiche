package it.infog.psiche.ui.git;

import it.infog.psiche.book.Chapter;
import it.infog.psiche.git.BookRepository;
import it.infog.psiche.git.model.FileRevision;
import it.infog.psiche.ui.common.ErrorReporter;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The commits that changed the open chapter, newest first, with the lines each one added and removed. Double-clicking
 * a commit, or pressing Enter on it, reads the chapter as that commit recorded it and hands it to a {@link
 * RevisionOpener}.
 */
public final class HistoryPane extends VBox {

    public static final DateTimeFormatter COMMIT_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    // Reading the history walks every commit, so it runs off the FX thread.
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("history").daemon().factory());

    private final ListView<FileRevision> revisions = new ListView<>();
    private final Label placeholder = new Label();
    private final BooleanProperty showing = new SimpleBooleanProperty();
    private final ErrorReporter errors;
    private final RevisionOpener opener;

    private BookRepository repository;
    private Chapter chapter;
    private Task<List<FileRevision>> loading;

    /** Receives a past version of a chapter the author asked to see. Called on the FX thread. */
    @FunctionalInterface
    public interface RevisionOpener {

        /** @param text the chapter as {@code revision} recorded it; empty if the chapter did not exist then */
        void open(Chapter chapter, FileRevision revision, Optional<String> text);
    }

    public HistoryPane(ErrorReporter errors, RevisionOpener opener) {
        this.errors = errors;
        this.opener = opener;
        revisions.setCellFactory(list -> new RevisionCell());
        revisions.setPlaceholder(placeholder);
        revisions.setOnKeyPressed(event -> {
            FileRevision selected = revisions.getSelectionModel().getSelectedItem();
            if (event.getCode() == KeyCode.ENTER && selected != null) {
                openRevision(selected);
            }
        });
        // Read the history each time the pane comes into view, so commits made outside the editor show up too.
        showing.addListener((obs, wasShowing, isShowing) -> {
            if (isShowing) {
                load();
            }
        });
        Label hint = new Label("Double-click a version to compare it with the saved chapter or restore it.");
        hint.getStyleClass().add("history-hint");
        hint.setWrapText(true);
        VBox.setVgrow(revisions, Priority.ALWAYS);
        getChildren().addAll(hint, revisions);
        getStyleClass().add("history");
        load();
    }

    /** Whether the pane is on screen. History is read only while it is; bind it to the tab's selection. */
    public BooleanProperty showingProperty() {
        return showing;
    }

    /**
     * Shows the history of {@code chapter}.
     *
     * @param repository the open book's repository, or {@code null} if it has none
     * @param chapter    the open chapter, or {@code null} if none is open
     */
    public void show(BookRepository repository, Chapter chapter) {
        this.repository = repository;
        this.chapter = chapter;
        refresh();
    }

    /** Reads the history again, for example after a commit; waits until the pane is showing. */
    public void refresh() {
        if (showing.get()) {
            load();
        }
    }

    private void load() {
        if (loading != null) {
            // A cancelled task never reports success, so a slow load cannot overwrite a newer one.
            loading.cancel(false);
            loading = null;
        }
        revisions.getItems().clear();
        if (chapter == null) {
            placeholder.setText("Open a chapter to see its history.");
            return;
        }
        if (repository == null) {
            placeholder.setText("This book has no Git repository,\nso its chapters have no history.");
            return;
        }
        placeholder.setText("Reading history…");
        BookRepository repo = repository;
        Path file = chapter.file();
        String title = chapter.title();
        Task<List<FileRevision>> task = new Task<>() {
            @Override
            protected List<FileRevision> call() {
                return repo.history(file);
            }
        };
        task.setOnSucceeded(event -> {
            placeholder.setText("No commit includes this chapter yet.\nCommit to start its history.");
            revisions.getItems().setAll(task.getValue());
        });
        task.setOnFailed(event -> {
            placeholder.setText("The history could not be read.");
            errors.report("Cannot read the history of " + title, task.getException());
        });
        loading = task;
        executor.execute(task);
    }

    /** Reads the open chapter as {@code revision} recorded it, then hands it to the opener. */
    private void openRevision(FileRevision revision) {
        if (repository == null || chapter == null) {
            return;
        }
        BookRepository repo = repository;
        Chapter target = chapter;
        Task<Optional<String>> task = new Task<>() {
            @Override
            protected Optional<String> call() {
                return repo.contentOf(revision);
            }
        };
        task.setOnSucceeded(event -> opener.open(target, revision, task.getValue()));
        task.setOnFailed(event -> errors.report(
                "Cannot read %s at %s".formatted(target.title(), revision.shortId()), task.getException()));
        executor.execute(task);
    }

    /** Stops the history thread, interrupting a running read. */
    public void shutdown() {
        executor.shutdownNow();
    }

    /** The chapter title a repository path stands for: its file name without the Markdown extension. */
    public static String chapterTitle(String repositoryPath) {
        String name = repositoryPath.substring(repositoryPath.lastIndexOf('/') + 1);
        return name.endsWith(".md") ? name.substring(0, name.length() - ".md".length()) : name;
    }

    /**
     * The commit message, then the lines added and removed, the short commit id and when it was committed, and the
     * chapter's previous name if the commit renamed it.
     */
    private final class RevisionCell extends ListCell<FileRevision> {

        private final Label message = new Label();
        private final Label added = new Label();
        private final Label removed = new Label();
        private final Label details = new Label();
        private final VBox content;

        RevisionCell() {
            message.getStyleClass().add("revision-message");
            added.getStyleClass().add("lines-added");
            removed.getStyleClass().add("lines-removed");
            details.getStyleClass().add("revision-details");
            HBox stats = new HBox(added, removed, details);
            stats.getStyleClass().add("revision-stats");
            content = new VBox(message, stats);
            getStyleClass().add("revision");
            setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !isEmpty()) {
                    openRevision(getItem());
                }
            });
        }

        @Override
        protected void updateItem(FileRevision revision, boolean empty) {
            super.updateItem(revision, empty);
            setText(null);
            if (empty || revision == null) {
                setGraphic(null);
                return;
            }
            message.setText(revision.message());
            added.setText("+" + revision.linesAdded());
            removed.setText("−" + revision.linesRemoved());
            String when = "%s · %s".formatted(revision.shortId(), COMMIT_TIME.format(revision.time()));
            details.setText(revision.renamedFrom() == null
                    ? when
                    : "%s · renamed from %s".formatted(when, chapterTitle(revision.renamedFrom())));
            setGraphic(content);
        }
    }
}
