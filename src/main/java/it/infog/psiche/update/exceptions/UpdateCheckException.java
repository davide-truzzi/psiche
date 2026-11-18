package it.infog.psiche.update.exceptions;

/** The latest release could not be learnt from psiche.app; the message says why, in words for the author. */
public class UpdateCheckException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UpdateCheckException(String message, Throwable cause) {

        super(message, cause);
    }
}
