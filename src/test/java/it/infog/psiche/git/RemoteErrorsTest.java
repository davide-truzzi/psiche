package it.infog.psiche.git;

import it.infog.psiche.git.model.enums.RemoteState;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertPathValidatorException;
import javax.net.ssl.SSLHandshakeException;

import static org.assertj.core.api.Assertions.assertThat;

/** JGit's failures as JGit 7.8 words them for HTTPS remotes, from its JGitText messages. */
class RemoteErrorsTest {

    private static final String ADDRESS = "https://github.com/davide/alba.git";

    @Test
    void aMissingRepositoryIsNotARepository() throws Exception {

        assertThat(RemoteErrors.state(new NoRemoteRepositoryException(new URIish(ADDRESS), ": not found.")))
                .contains(RemoteState.NOT_A_REPOSITORY);
    }

    @Test
    void anUntrustedCertificateIsCertificate() {

        SSLHandshakeException handshake = new SSLHandshakeException("PKIX path building failed");
        handshake.initCause(new CertPathValidatorException("unable to find valid certification path"));

        assertThat(RemoteErrors.state(new TransportException(ADDRESS + ": Secure connection could not be established",
                handshake))).contains(RemoteState.CERTIFICATE);
    }

    @Test
    void anUnknownHostOrNoAnswerIsUnreachable() {

        assertThat(RemoteErrors.state(new TransportException(ADDRESS + ": cannot open git-upload-pack",
                new UnknownHostException("github.com")))).contains(RemoteState.UNREACHABLE);
        assertThat(RemoteErrors.state(new TransportException(ADDRESS + ": cannot open git-upload-pack",
                new ConnectException("Connection refused")))).contains(RemoteState.UNREACHABLE);
        assertThat(RemoteErrors.state(new TransportException(ADDRESS + ": cannot open git-upload-pack",
                new SocketTimeoutException("Read timed out")))).contains(RemoteState.UNREACHABLE);
    }

    @Test
    void refusedCredentialsAreCredentials() {

        assertThat(RemoteErrors.state(new TransportException(ADDRESS + ": not authorized")))
                .contains(RemoteState.CREDENTIALS);
        assertThat(RemoteErrors.state(new TransportException(ADDRESS + ": Authentication is required but no "
                + "CredentialsProvider has been registered"))).contains(RemoteState.CREDENTIALS);
    }

    @Test
    void noRightToWriteIsReadOnly() {

        assertThat(RemoteErrors.state(new TransportException(ADDRESS + ": git-receive-pack not permitted on '"
                + ADDRESS + "'"))).contains(RemoteState.READ_ONLY);
    }

    @Test
    void anythingElseIsUnknown() {

        assertThat(RemoteErrors.state(new TransportException(ADDRESS + ": unexpected end of stream"))).isEmpty();
    }

    @Test
    void messagesLoseTheUsernamesAndPasswordsOfAddresses() {

        assertThat(RemoteErrors.cleaned("https://davide:ghp_secret@github.com/davide/alba.git: not authorized"))
                .isEqualTo("https://github.com/davide/alba.git: not authorized");
        assertThat(RemoteErrors.cleaned("cannot reach https://davide@github.com/a and http://x:y@h/b"))
                .isEqualTo("cannot reach https://github.com/a and http://h/b");
        assertThat(RemoteErrors.cleaned(null)).isEmpty();
    }
}
