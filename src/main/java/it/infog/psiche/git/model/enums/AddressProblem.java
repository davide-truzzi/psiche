package it.infog.psiche.git.model.enums;

/** Why an address typed for a book's remote repository is refused. */
public enum AddressProblem {

    BLANK("Type the repository's address, or choose a folder."),
    NOT_SECURE("Use the https:// address: http:// sends the token unencrypted."),
    GIT_PROTOCOL("Use the https:// address: git:// is unencrypted and cannot send the book."),
    SSH("SSH addresses are not supported: use the https:// address."),
    CREDENTIALS_IN_ADDRESS("Leave the username and token out of the address: they have fields of their own."),
    UNKNOWN("Not an https:// address, nor a folder's full path.");

    private final String message;

    AddressProblem(String message) {

        this.message = message;
    }

    /** What the author is told, with what to do instead. */
    public String message() {

        return message;
    }
}
