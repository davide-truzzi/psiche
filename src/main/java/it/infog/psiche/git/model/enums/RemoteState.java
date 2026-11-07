package it.infog.psiche.git.model.enums;

/**
 * What Test found at a book's remote: what the repository holds compared with the book, or why it cannot be
 * reached or written to.
 */
public enum RemoteState {

    // No revisions yet: the book's are sent at the first Synchronize.
    EMPTY(true, "The repository is empty: Synchronize will send the book to it."),
    SAME(true, "The repository has the book as it is."),
    // Every revision there is in the book; the book has newer ones.
    BEHIND(true, "The book has newer revisions: Synchronize will send them."),
    // The book's revisions and newer ones, from another device.
    AHEAD(true, "This repository has newer revisions; Synchronize will bring them in."),
    // The book and the repository both have revisions the other lacks, from a shared start.
    DIVERGED(true, "This repository has newer revisions; Synchronize will bring them in."),
    // No revision in common, as with a repository the host started with a README.
    UNRELATED(false, "This repository holds something else than this book. If it was created with a README, a "
            + "licence or a .gitignore, create a new one without any of them and connect to that. If it holds this "
            + "book from another device, import a Psiche project of it instead."),
    UNREACHABLE(false, "The repository cannot be reached: check the address and the connection."),
    CERTIFICATE(false, "The host's certificate is not trusted, so the connection is not safe."),
    // Hosts such as GitHub answer so for a repository that is not there too, so as not to tell that it exists.
    CREDENTIALS(false, "The host refused the username or the token, or there is no repository at this address."),
    READ_ONLY(false, "The token cannot write to this repository: give it the right to write (contents: read and "
            + "write), or make a new one that has it."),
    NOT_A_REPOSITORY(false, "There is no repository at this address.");

    private final boolean accepted;
    private final String message;

    RemoteState(boolean accepted, String message) {

        this.accepted = accepted;
        this.message = message;
    }

    /** True if the book can be connected to the repository. */
    public boolean accepted() {

        return accepted;
    }

    /** What the author is told after Test. */
    public String message() {

        return message;
    }
}
