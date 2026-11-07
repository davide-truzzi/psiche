package it.infog.psiche.git.exceptions;

import it.infog.psiche.git.model.enums.RemoteState;

/** The book's remote could not be reached or used, for a known reason: its {@link RemoteState}. */
public class RemoteStateException extends GitCommandException {

    private static final long serialVersionUID = 1L;

    private final RemoteState state;

    public RemoteStateException(RemoteState state, Throwable cause) {

        super(state.message(), cause);
        this.state = state;
    }

    /** Why the remote failed: {@link RemoteState#UNREACHABLE}, {@code CREDENTIALS}, and so on. */
    public RemoteState state() {

        return state;
    }
}
