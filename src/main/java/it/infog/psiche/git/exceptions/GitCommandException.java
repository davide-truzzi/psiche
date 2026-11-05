package it.infog.psiche.git.exceptions;

public class GitCommandException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GitCommandException(String message) {

        super(message);
    }

    public GitCommandException(String message, Throwable cause) {

        super(message, cause);
    }
}
