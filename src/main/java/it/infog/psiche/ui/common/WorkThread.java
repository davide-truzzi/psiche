package it.infog.psiche.ui.common;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A thread of its own for long work kept off the FX thread, such as copying a whole book: one piece of work at a time,
 * in order, marked busy meanwhile, so the window can wait for it; what it gives, or its failure, is handed back on the
 * FX thread.
 */
public final class WorkThread {

    private final ExecutorService thread;
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper();

    /** @param name the thread's name, as a profiler or a thread dump shows it */
    public WorkThread(String name) {

        thread = Executors.newSingleThreadExecutor(Thread.ofPlatform().name(name).daemon().factory());
    }

    /** True while some work runs. */
    public ReadOnlyBooleanProperty busyProperty() {

        return busy.getReadOnlyProperty();
    }

    public boolean isBusy() {

        return busy.get();
    }

    /**
     * Runs {@code work} on the thread, marked busy from now until it is done; then hands its result to {@code done},
     * or its failure to {@code failed}, on the FX thread.
     */
    public <T> void run(Callable<T> work, Consumer<T> done, Consumer<Throwable> failed) {

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> {
            busy.set(false);
            done.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            busy.set(false);
            failed.accept(task.getException());
        });
        busy.set(true);
        thread.execute(task);
    }

    /** Stops the thread, interrupting the work that runs, without waiting for it. */
    public void shutdown() {

        thread.shutdownNow();
    }

    /**
     * Stops the thread, interrupting the work that runs, and waits up to {@code seconds} for it to clean up.
     *
     * @return false if it was still running when the wait ended
     */
    public boolean shutdown(long seconds) throws InterruptedException {

        thread.shutdownNow();
        return thread.awaitTermination(seconds, TimeUnit.SECONDS);
    }
}
