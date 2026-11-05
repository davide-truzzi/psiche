package it.infog.psiche.ui.common;

import it.infog.psiche.common.CopyProgress;
import javafx.application.Platform;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/** How far a long copy has come, as the window shows it. */
public final class Progress {

    private Progress() {
    }

    /**
     * What hears how far a copy has come, on any thread, and hands {@code shown} the percentage on the FX thread each
     * time it changes, so the window is not flooded with updates.
     */
    public static CopyProgress percent(IntConsumer shown) {

        AtomicInteger last = new AtomicInteger(-1);
        return (copied, total) -> {
            int percent = total <= 0 ? 100 : (int) (copied * 100 / total);
            if (last.getAndSet(percent) != percent)
                Platform.runLater(() -> shown.accept(percent));
        };
    }
}
