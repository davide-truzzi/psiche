package it.infog.psiche.git;

import it.infog.psiche.git.model.enums.RemoteState;

import org.eclipse.jgit.errors.NoRemoteRepositoryException;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;

/**
 * What JGit's failures to reach a remote mean to the author, and its messages made safe to show: an address with a
 * username or a password in it loses them.
 */
final class RemoteErrors {

    // The user info of an address, user:password@ or user@, after its scheme.
    private static final Pattern USER_INFO = Pattern.compile("(?<=://)[^/@\\s]+@");
    // JGit's own words, in English only, for an HTTP 401 and for credentials it was not given.
    private static final String NOT_AUTHORIZED = "not authorized";
    private static final String AUTHENTICATION = "authentication";
    // JGit's words for an HTTP 403 on git-receive-pack: the token may read but not write.
    private static final String NOT_PERMITTED = "not permitted";

    private RemoteErrors() {
    }

    /** What {@code failure}, thrown while reaching a remote, means; empty if it is none of the known causes. */
    static Optional<RemoteState> state(Exception failure) {

        if (failure instanceof NoRemoteRepositoryException)
            return Optional.of(RemoteState.NOT_A_REPOSITORY);

        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SSLException || cause instanceof GeneralSecurityException)
                return Optional.of(RemoteState.CERTIFICATE);

            if (cause instanceof UnknownHostException || cause instanceof ConnectException
                    || cause instanceof NoRouteToHostException || cause instanceof SocketTimeoutException)
                return Optional.of(RemoteState.UNREACHABLE);
        }

        String message = String.valueOf(failure.getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains(NOT_PERMITTED))
            return Optional.of(RemoteState.READ_ONLY);

        if (message.contains(NOT_AUTHORIZED) || message.contains(AUTHENTICATION))
            return Optional.of(RemoteState.CREDENTIALS);

        return Optional.empty();
    }

    /** {@code message} without the usernames and passwords of the addresses in it. */
    static String cleaned(String message) {

        return message == null ? "" : USER_INFO.matcher(message).replaceAll("");
    }
}
