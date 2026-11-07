package it.infog.psiche.git.exceptions;

/** Synchronize refused: the book has changes that are not committed, which a merge would mix with the remote's. */
public class UncommittedWorkException extends GitCommandException {

    private static final long serialVersionUID = 1L;

    public UncommittedWorkException() {

        super("The book has changes that are not committed: commit them first, then Synchronize.");
    }
}
