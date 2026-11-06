package it.infog.psiche.ui.media;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.media.ChapterMedia;
import it.infog.psiche.media.DetachedMedia;
import it.infog.psiche.media.model.DetachedGroup;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;
import it.infog.psiche.ui.common.WorkThread;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.stage.Window;

import java.util.List;
import java.util.function.Supplier;

/**
 * The images left without a chapter: lists them for the author to put back in a chapter's images or delete for good,
 * and offers a restored chapter the images it had when it was deleted. It works without Git, as images are not in it.
 * Images are read and moved on the Media tab's thread, in order with its own work, and the tab shows those put back in
 * its chapter at once.
 */
public final class DetachedImages {

    private static final String READ_FAILURE = "Cannot read the detached images";

    private final MediaPane mediaPane;
    private final WorkThread thread;
    private final StatusBar statusBar;
    private final ErrorReporter errors;
    private final Supplier<Window> window;
    private final Supplier<BookFolder> book;
    private final Supplier<List<String>> chapters;
    private final ReadOnlyBooleanWrapper any = new ReadOnlyBooleanWrapper();

    // The images last asked about, so an answer about another book's, read before it closed, is dropped.
    private BookFolder asked;
    // True from a click on Detached images until its dialog closes, so a second click opens no second one.
    private boolean opening;

    /**
     * @param book     the open book; {@code null} with none open
     * @param chapters the open book's chapters, by name, in their order
     */
    public DetachedImages(MediaPane mediaPane, StatusBar statusBar, ErrorReporter errors, Supplier<Window> window,
                          Supplier<BookFolder> book, Supplier<List<String>> chapters) {

        this.mediaPane = mediaPane;
        this.thread = mediaPane.thread();
        this.statusBar = statusBar;
        this.errors = errors;
        this.window = window;
        this.book = book;
        this.chapters = chapters;
    }

    /** Lists the images the open book keeps for none of its chapters, to restore or delete, until closed. */
    public void show() {

        DetachedMedia store = store();
        List<String> chapters = this.chapters.get();
        if (opening || store == null)
            return;

        opening = true;
        thread.run(() -> store.orphans(chapters), groups -> {
            DetachedImagesDialog dialog = new DetachedImagesDialog(window.get(), chapters);
            dialog.setOnRestore((group, chapter) -> {
                restore(store, group, chapter);
                refill(dialog, store, chapters);
            });
            dialog.setOnDelete(group -> {
                delete(store, group);
                refill(dialog, store, chapters);
            });
            dialog.showAndWait(groups);
            opening = false;
        }, failure -> {
            errors.report(READ_FAILURE, failure);
            opening = false;
        });
    }

    /** True while the book keeps images for no chapter, as {@link #check} last found: Detached images shows then. */
    public ReadOnlyBooleanProperty anyProperty() {

        return any.getReadOnlyProperty();
    }

    /**
     * Looks, on the Media tab's thread, whether the open book keeps images for none of its chapters, and keeps the
     * answer in {@link #anyProperty}; with no book open there are none.
     */
    public void check() {

        BookFolder open = book.get();
        DetachedMedia store = store();
        List<String> chapters = this.chapters.get();
        asked = open;
        if (store == null) {
            any.set(false);
            return;
        }

        thread.run(() -> store.hasOrphans(chapters), has -> {
            if (asked == open)
                any.set(has);
        }, failure -> errors.report(READ_FAILURE, failure));
    }

    /** Asks, after {@code chapter} is restored, whether the images it had when it was deleted come back too. */
    public void offerRestore(String chapter) {

        DetachedMedia store = store();
        if (store == null)
            return;

        thread.run(() -> store.latestDeleted(chapter), group -> {
            if (group.isPresent() && DetachedImagesDialog.askRestore(window.get(), group.get()))
                restore(store, group.get(), chapter);
        }, failure -> errors.report("Cannot read the images of " + chapter, failure));
    }

    /** The open book's detached images; {@code null} with no book open. */
    private DetachedMedia store() {

        BookFolder open = book.get();
        return open == null ? null : new ChapterMedia(open.mediaDir()).detached();
    }

    /** Shows in {@code dialog} the groups left after the change asked for, or closes it if they can't be read. */
    private void refill(DetachedImagesDialog dialog, DetachedMedia store, List<String> chapters) {

        thread.run(() -> store.orphans(chapters), dialog::showGroups, failure -> {
            errors.report(READ_FAILURE, failure);
            dialog.close();
        });
    }

    private void restore(DetachedMedia store, DetachedGroup group, String chapter) {

        thread.run(() -> {
            store.restoreTo(group, chapter);
            return chapter;
        }, done -> {
            mediaPane.reload();
            statusBar.info("Restored %s to %s".formatted(DetachedImagesDialog.count(group), chapter));
            check();
        }, failure -> errors.report("Cannot restore the images of " + group.chapter(), failure));
    }

    private void delete(DetachedMedia store, DetachedGroup group) {

        thread.run(() -> {
            store.deletePermanently(group);
            return group;
        }, done -> {
            statusBar.info("Deleted the images of %s for good".formatted(group.chapter()));
            check();
        }, failure -> errors.report("Cannot delete the images of " + group.chapter(), failure));
    }
}
