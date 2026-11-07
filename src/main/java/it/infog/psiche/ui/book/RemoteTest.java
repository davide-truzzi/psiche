package it.infog.psiche.ui.book;

import it.infog.psiche.book.BookFolder;
import it.infog.psiche.git.BookRepository;
import it.infog.psiche.git.RemoteFolder;
import it.infog.psiche.git.model.RemoteAddress;
import it.infog.psiche.git.model.enums.FolderState;
import it.infog.psiche.git.model.enums.RemoteKind;
import it.infog.psiche.git.model.enums.RemoteState;
import it.infog.psiche.settings.model.Credentials;
import it.infog.psiche.ui.git.GitControls;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Test, in the Remote repository section of Book properties: what an address holds, checked on the Git thread, and
 * how the author is told.
 */
final class RemoteTest {

    /** How a Test's result reads: good, good with something to know, or refused. */
    enum Tone {

        OK("remote-ok"),
        NOTE("remote-note"),
        WRONG("remote-wrong");

        private final String styleClass;

        Tone(String styleClass) {

            this.styleClass = styleClass;
        }

        /** The style class of a text in this tone. */
        String styleClass() {

            return styleClass;
        }
    }

    /**
     * What a Test found, as the author is told.
     *
     * @param takesBook true if Save brings the repository's book into this empty one
     */
    record Outcome(boolean accepted,
                   String message,
                   Tone tone,
                   boolean takesBook) {

        Outcome(boolean accepted, String message, Tone tone) {

            this(accepted, message, tone, false);
        }
    }

    // Longer than the check as a book opens: the author is waiting for this one, and a slow host still answers.
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String SAME_DRIVE = "This folder is on the same drive as the book: if that disk fails, "
            + "both copies are lost. Choose another drive if you can.";

    private final GitControls git;
    private final Path book;

    /**
     * @param git  reads the saved credentials of the book's remote
     * @param book the book's folder
     */
    RemoteTest(GitControls git, Path book) {

        this.git = git;
        this.book = book;
    }

    /**
     * What Test finds at {@code target}, on the Git thread, for the book titled {@code bookTitle} in the form.
     *
     * @param typed the credentials typed in the form; none to use the saved ones
     */
    Outcome outcome(BookRepository repository, RemoteAddress target, Optional<Credentials> typed,
                    String bookTitle) {

        Optional<Path> folder = target.kind() == RemoteKind.FOLDER ? Optional.of(Path.of(target.address()))
                : Optional.empty();
        Optional<FolderState> refused = folder.map(chosen -> RemoteFolder.state(chosen, book))
                .filter(state -> !state.usable());
        if (refused.isPresent())
            return new Outcome(false, refused.get().problem(), Tone.WRONG);

        Optional<Credentials> credentials = typed.isPresent() ? typed : git.savedCredentials(target.kind());
        // An empty book may take the repository's, if it has the same title.
        Optional<String> emptyBookTitle = BookFolder.open(book).chapters().isEmpty() ? Optional.of(bookTitle)
                : Optional.empty();
        RemoteState state = repository.remote().test(target, credentials, emptyBookTitle, TIMEOUT);
        if (!state.accepted())
            return new Outcome(false, state.message(), Tone.WRONG);

        boolean sameDrive = folder.filter(chosen -> RemoteFolder.samePartition(chosen, book)).isPresent();
        boolean takes = state == RemoteState.HOLDS_BOOK;
        boolean newer = takes || state == RemoteState.AHEAD || state == RemoteState.DIVERGED;
        return new Outcome(true, sameDrive ? state.message() + " " + SAME_DRIVE : state.message(),
                sameDrive || newer ? Tone.NOTE : Tone.OK, takes);
    }
}
