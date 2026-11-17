package it.infog.psiche.ui.preview;

import com.sun.webkit.Invoker;
import javafx.application.Platform;
import javafx.scene.web.WebEngine;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Keeps the web engine's timer from firing more than about sixty times a second. Its timers wait on a thread of their
 * own (see WriterApp), which hands each one due to the JavaFX thread at once, with no least wait between them. Some
 * pages keep a timer that sets itself again to fire at once every time it fires: a long chapter with indented
 * paragraphs and an italic block quote is one. The engine then fires it a hundred and fifty thousand times a second,
 * taking a processor and a half for as long as the chapter shows. Timers driven by the screen's pulses, the engine's
 * default, would fire it at most once a frame: this spaces the timer's hand-overs as far apart as frames, and leaves
 * every other hand-over the engine makes as it is.
 * <p>
 * It stands in for the engine's own go-between with the JavaFX thread, an internal class of JavaFX: the build exports
 * its package to the app.
 */
final class PacedWebTimer extends Invoker {

    // The thread the engine's timer waits on and hands its timers over from.
    private static final String TIMER_THREAD = "WebPane-Timer";
    private static final long LEAST_WAIT_NANOS = TimeUnit.MILLISECONDS.toNanos(16);

    private final Invoker engines;
    private final ScheduledExecutorService later =
            Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("web-timer-pace").daemon().factory());
    // When the timer may next be handed over; read and written by the timer's thread only.
    private long nextNanos;

    private PacedWebTimer(Invoker engines) {

        this.engines = engines;
    }

    /**
     * Puts the pace in place of the engine's go-between, before the first web engine is made, which picks the timer.
     * The engine's class sets its own go-between when it loads, so it is loaded first.
     */
    static void install() {

        try {
            Class.forName(WebEngine.class.getName(), true, WebEngine.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        Invoker.setInvoker(new PacedWebTimer(Invoker.getInvoker()));
    }

    @Override
    public void invokeOnEventThread(Runnable runnable) {

        if (!Thread.currentThread().getName().equals(TIMER_THREAD)) {
            engines.invokeOnEventThread(runnable);
            return;
        }

        long now = System.nanoTime();
        long wait = nextNanos - now;
        nextNanos = Math.max(now, nextNanos) + LEAST_WAIT_NANOS;
        if (wait <= 0)
            engines.invokeOnEventThread(runnable);
        else
            later.schedule(() -> engines.invokeOnEventThread(runnable), wait, TimeUnit.NANOSECONDS);
    }

    @Override
    public void postOnEventThread(Runnable runnable) {

        engines.postOnEventThread(runnable);
    }

    @Override
    public void checkEventThread() {

        engines.checkEventThread();
    }

    @Override
    protected boolean isEventThread() {

        return Platform.isFxApplicationThread();
    }

    // No locks, as in the engine's own go-between: JavaFX keeps its pulses and rendering in step itself.
    @Override
    protected boolean lock(ReentrantLock lock) {

        return false;
    }

    @Override
    protected boolean unlock(ReentrantLock lock) {

        return false;
    }
}
