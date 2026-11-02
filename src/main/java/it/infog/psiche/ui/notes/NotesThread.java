package it.infog.psiche.ui.notes;

import it.infog.psiche.notes.NoteStore;
import it.infog.psiche.ui.common.ErrorReporter;

import javafx.application.Platform;
import javafx.concurrent.Task;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The thread the board's notes are read and written on, one piece of work at a time in the order it was given, so the
 * window never waits for the disk and changes reach it in the order they were made. It knows which chapter's board is
 * shown: work is done on the notes of that board, even if another opens before it runs, and what is read is handed
 * back only if the same board is still shown.
 */
final class NotesThread {

    /** Work on a chapter's notes. */
    @FunctionalInterface
    interface Work {

        void run(NoteStore store, String chapter);
    }

    /** A reading of a chapter's notes, or a change that gives something back. */
    @FunctionalInterface
    interface Reading<T> {

        T read(NoteStore store, String chapter);
    }

    private final ExecutorService thread =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name("notes").daemon().factory());
    private final ErrorReporter errors;

    private NoteStore store;
    private String chapter;
    // Counts the boards shown, so what is read for one is never handed to another, even to the same chapter reopened.
    private int board;

    NotesThread(ErrorReporter errors) {

        this.errors = errors;
    }

    /** The open book's notes, or {@code null} with no book open. */
    NoteStore store() {

        return store;
    }

    /** The open chapter's name, or {@code null} with none open. */
    String chapter() {

        return chapter;
    }

    /** True if a chapter's board is shown. */
    boolean hasBoard() {

        return store != null && chapter != null;
    }

    /** True if {@code store} and {@code chapter} are the board shown now. */
    boolean shows(NoteStore store, String chapter) {

        return store == this.store && Objects.equals(chapter, this.chapter);
    }

    /** From now on, works on the notes of {@code chapter} in {@code store}; either may be {@code null}. */
    void show(NoteStore store, String chapter) {

        this.store = store;
        this.chapter = chapter;
        board++;
    }

    /**
     * Runs {@code reading} on the thread, then hands what it gives to {@code done}, on the FX thread, if the same board
     * is still shown. A failure is reported as {@code failure}.
     */
    <T> void read(String failure, Reading<T> reading, Consumer<T> done) {

        if (!hasBoard())
            return;

        NoteStore target = store;
        String title = chapter;
        int shown = board;
        Task<T> task = new Task<>() {
            @Override
            protected T call() {
                return reading.read(target, title);
            }
        };
        task.setOnSucceeded(event -> {
            if (shown == board)
                done.accept(task.getValue());
        });
        task.setOnFailed(event -> errors.report(failure, task.getException()));
        thread.execute(task);
    }

    /** Runs {@code work} on the thread, and reports a failure as {@code failure}. */
    void write(String failure, Work work) {

        write(failure, work, () -> {});
    }

    /** Runs {@code work} on the thread, then {@code done} on the FX thread; or reports a failure as {@code failure}. */
    void write(String failure, Work work, Runnable done) {

        if (!hasBoard())
            return;

        NoteStore target = store;
        String title = chapter;
        thread.execute(() -> {
            try {
                work.run(target, title);
                Platform.runLater(done);
            } catch (RuntimeException e) {
                Platform.runLater(() -> errors.report(failure, e));
            }
        });
    }

    /**
     * Runs {@code work} on the thread, after the work given before it, whichever board is shown: work on the book's
     * notes rather than a board's, such as Detached notes. Then hands what it gives to {@code done}, or its failure to
     * {@code failed}, both on the FX thread.
     */
    <T> void run(Supplier<T> work, Consumer<T> done, Consumer<Throwable> failed) {

        Task<T> task = new Task<>() {
            @Override
            protected T call() {
                return work.get();
            }
        };
        task.setOnSucceeded(event -> done.accept(task.getValue()));
        task.setOnFailed(event -> failed.accept(task.getException()));
        thread.execute(task);
    }

    /** Makes {@code change} to the book's notes as {@link #run} does, then runs {@code done}, or {@code failed}. */
    void change(Runnable change, Runnable done, Consumer<Throwable> failed) {

        run(() -> {
            change.run();
            return true;
        }, changed -> done.run(), failed);
    }

    /** Waits for the thread to finish the work it was given, so a chapter can be renamed or deleted safely. */
    void settle() {

        try {
            thread.submit(() -> {}).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            errors.report("Some notes may not be saved", e);
        }
    }

    /** Stops the thread once it has finished the work it was given. */
    void shutdown() {

        thread.shutdown();
    }
}
