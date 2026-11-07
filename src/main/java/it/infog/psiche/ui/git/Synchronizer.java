package it.infog.psiche.ui.git;

import it.infog.psiche.git.BookRepository;
import it.infog.psiche.git.exceptions.RemoteStateException;
import it.infog.psiche.git.exceptions.UncommittedWorkException;
import it.infog.psiche.git.model.SyncReport;
import it.infog.psiche.git.model.enums.RemoteKind;
import it.infog.psiche.git.model.enums.RemoteState;
import it.infog.psiche.settings.Keychain;
import it.infog.psiche.ui.common.Dialogs;
import it.infog.psiche.ui.common.ErrorReporter;
import it.infog.psiche.ui.common.StatusBar;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Synchronize, on the Git thread of {@link GitControls}, in one of two ways, as the book's switch says. Manual: only
 * when the author clicks it, nothing asked or checked by itself. Automatic: also as the book opens, if its remote has
 * something new, after every commit, and before the book closes.
 * <p>
 * What runs by itself never blocks the author: a remote that cannot be reached is only told in the status bar, and the
 * book opens or closes anyway.
 */
final class Synchronizer {

    private static final Logger log = LoggerFactory.getLogger(Synchronizer.class);

    // As a book opens or closes the author waits, so a remote that does not answer is given up soon.
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(10);
    // A remote that is not there: offline, or a drive that is not plugged in.
    private static final Set<RemoteState> UNREACHABLE = EnumSet.of(RemoteState.UNREACHABLE,
            RemoteState.NOT_A_REPOSITORY);
    private static final String OFFLINE = "Remote not reachable; working locally";
    private static final String LOCKED = "Not synchronized: the keychain is locked";

    private final GitControls git;
    private final Keychain keychain;
    private final StatusBar statusBar;
    private final ErrorReporter errors;
    private final ReadOnlyBooleanWrapper synchronizing = new ReadOnlyBooleanWrapper();

    private BooleanSupplier unsavedWork = () -> false;
    private Consumer<SyncReport> afterSynchronize = report -> {};
    private boolean autoSync;
    // True while the book is being left: one more request to close it is not another Synchronize.
    private boolean leaving;

    Synchronizer(GitControls git, Keychain keychain, StatusBar statusBar, ErrorReporter errors) {

        this.git = git;
        this.keychain = keychain;
        this.statusBar = statusBar;
        this.errors = errors;
    }

    /** True while Synchronize runs, which may change the book's files: nothing else may change them meanwhile. */
    ReadOnlyBooleanProperty synchronizingProperty() {

        return synchronizing.getReadOnlyProperty();
    }

    void setSynchronization(BooleanSupplier unsavedWork, Consumer<SyncReport> afterSynchronize) {

        this.unsavedWork = unsavedWork;
        this.afterSynchronize = afterSynchronize;
    }

    /** Whether the open book synchronizes by itself, as it opens and closes and after every commit. */
    void setAutoSync(boolean autoSync) {

        this.autoSync = autoSync;
    }

    /**
     * The author's Synchronize: refused while work is not saved and committed, before the keychain is asked for, and
     * said so in a dialog, as is anything that goes wrong.
     */
    void clicked() {

        if (unsavedWork.getAsBoolean()) {
            SyncReports.commitFirst(git.getScene().getWindow());
            return;
        }

        // Saved but not committed is known only from the disk, so on the Git thread.
        git.run("Synchronize", BookRepository::isCommitted, committed -> {
            if (committed)
                synchronize(Duration.ZERO, this::clickedFailed, () -> {});
            else
                notCommitted();
        });
    }

    /**
     * As a book that synchronizes automatically opens: checks whether its remote has something new, and synchronizes
     * if so; nothing for a book synchronized by hand, or without a remote.
     */
    void checkAtOpen() {

        if (!autoSync || !git.hasRemote())
            return;

        RemoteKind kind = git.remoteKind();
        if (kind == RemoteKind.HTTPS && !KeychainDialogs.unlocked(git.getScene().getWindow(), keychain)) {
            statusBar.info("Remote not checked: the keychain is locked");
            return;
        }

        git.run("Checking the remote", repo -> repo.checkRemote(git.savedCredentials(kind), SHORT_TIMEOUT),
                this::checked, error -> {
                    if (unreachable(error))
                        statusBar.info(OFFLINE);
                    else
                        statusBar.error("Remote not checked: " + error.getMessage());
                });
    }

    /** After the author's commit: Synchronize, for a book that synchronizes automatically. */
    void afterCommit() {

        if (!autoSync || !git.hasRemote())
            return;

        // A chapter renamed while others are not saved: they would be read again from disk.
        if (unsavedWork.getAsBoolean()) {
            statusBar.info("Not synchronized: some chapters are not saved and committed");
            return;
        }

        synchronize(Duration.ZERO, this::automaticFailed, () -> {});
    }

    /**
     * Before the open book closes, once its unsaved chapters are settled: for a book that synchronizes automatically,
     * offers to commit work not committed yet, then synchronizes; then runs {@code then}, whatever the remote does.
     * Nothing runs if the author cancels.
     */
    void leave(Runnable then) {

        if (leaving)
            return;

        if (!autoSync || !git.hasRemote()) {
            then.run();
            return;
        }

        leaving = true;
        Runnable left = () -> {
            leaving = false;
            then.run();
        };
        git.run("Synchronize", BookRepository::isCommitted, committed -> {
            if (committed) {
                synchronize(SHORT_TIMEOUT, this::leavingFailed, left);
                return;
            }

            // A commit that fails keeps the book open, as cancelling does, with the work still there.
            boolean goesOn = SyncQuestions.commitBeforeClosing(git.getScene().getWindow(), git.commitMessage(),
                    message -> git.commit(message, () -> synchronize(SHORT_TIMEOUT, this::leavingFailed, left),
                            () -> leaving = false),
                    left);
            if (!goesOn)
                leaving = false;
        }, error -> {
            log.warn("Cannot read the state of the book before closing it", error);
            left.run();
        });
    }

    /**
     * What the check at open found: Synchronize run if the remote has something new. Newer revisions there that wait
     * for a commit here are told in a dialog, as the author clicking Synchronize is.
     */
    private void checked(RemoteState found) {

        switch (found) {
            case SAME -> statusBar.info("The remote has nothing new");
            case EMPTY, BEHIND -> synchronize(Duration.ZERO, this::automaticFailed, () -> {});
            case AHEAD, DIVERGED -> clicked();
            default -> statusBar.error(found.message());
        }
    }

    /**
     * Synchronizes the book, after unlocking the keychain for an HTTPS remote, and shows it as Synchronize left it;
     * then runs {@code then}, even if it failed.
     *
     * @param timeout how long to wait for the host to answer; zero for JGit's default
     * @param failure told what went wrong, before {@code then} runs
     */
    private void synchronize(Duration timeout, Consumer<Throwable> failure, Runnable then) {

        RemoteKind kind = git.remoteKind();
        if (kind == RemoteKind.HTTPS && !KeychainDialogs.unlocked(git.getScene().getWindow(), keychain)) {
            statusBar.info(LOCKED);
            then.run();
            return;
        }

        synchronizing.set(true);
        git.run("Synchronize", repo -> repo.synchronize(git.savedCredentials(kind), timeout), report -> {
            synchronizing.set(false);
            afterSynchronize.accept(report);
            statusBar.info(SyncReports.summary(report));
            SyncReports.showConflicts(git.getScene().getWindow(), report.conflicts());
            then.run();
        }, error -> {
            synchronizing.set(false);
            failure.accept(error);
            then.run();
        });
    }

    /** What went wrong with the author's Synchronize, in a dialog. */
    private void clickedFailed(Throwable error) {

        if (error instanceof UncommittedWorkException)
            notCommitted();
        else
            errors.report("Synchronize failed", error);
    }

    /** What went wrong with Synchronize run by itself: in the status bar while the remote cannot be reached. */
    private void automaticFailed(Throwable error) {

        if (error instanceof UncommittedWorkException)
            statusBar.info("Not synchronized: it waits for your next commit");
        else if (unreachable(error))
            statusBar.info(OFFLINE);
        else
            errors.report("Synchronize failed", error);
    }

    /** What went wrong with Synchronize as the book closes: said and waited for, as the window may close next. */
    private void leavingFailed(Throwable error) {

        if (unreachable(error)) {
            log.info("Not synchronized before closing: {}", error.getMessage());
            statusBar.info(OFFLINE);
            return;
        }

        log.warn("Not synchronized before closing", error);
        Dialogs.inform(git.getScene().getWindow(), "The book is not synchronized", error.getMessage());
    }

    /** Tells the author Synchronize waits for their work to be committed. */
    private void notCommitted() {

        statusBar.info("Not synchronized: commit first");
        SyncReports.commitFirst(git.getScene().getWindow());
    }

    /** True if {@code error} says the remote is not there: offline, or a drive that is not plugged in. */
    private static boolean unreachable(Throwable error) {

        return error instanceof RemoteStateException failed && UNREACHABLE.contains(failed.state());
    }
}
