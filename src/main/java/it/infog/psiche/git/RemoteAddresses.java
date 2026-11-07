package it.infog.psiche.git;

import it.infog.psiche.git.model.RemoteAddress;
import it.infog.psiche.git.model.enums.AddressProblem;
import it.infog.psiche.git.model.enums.RemoteKind;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Checks an address typed for a book's remote repository: an {@code https://} address with no username or password
 * in it, or the full path of a folder. Everything else is refused, saying why: {@code http://} and {@code git://},
 * which are unencrypted, and SSH, which Psiche does not speak.
 */
public final class RemoteAddresses {

    private static final String HTTPS = "https";
    private static final String FILE = "file";
    // SSH as Git writes it without a scheme, user@host:path or host:path; a host of one letter is a Windows drive.
    private static final Pattern SCP_LIKE = Pattern.compile("^([^@/\\\\]+@)?[^@/\\\\:]{2,}:.*");
    private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");

    private RemoteAddresses() {
    }

    /** Why {@code typed} cannot be a book's remote; empty if it can. */
    public static Optional<AddressProblem> problem(String typed) {

        if (typed == null || typed.isBlank())
            return Optional.of(AddressProblem.BLANK);

        String address = typed.strip();
        if (SCHEME.matcher(address).matches())
            return withScheme(address);

        if (SCP_LIKE.matcher(address).matches())
            return Optional.of(AddressProblem.SSH);

        return folder(address).isPresent() ? Optional.empty() : Optional.of(AddressProblem.UNKNOWN);
    }

    /**
     * {@code typed} as a remote's address.
     *
     * @throws IllegalArgumentException if it has a {@link #problem}
     */
    public static RemoteAddress read(String typed) {

        problem(typed).ifPresent(problem -> {
            throw new IllegalArgumentException(problem.message());
        });

        String address = typed.strip();
        return hasScheme(address, HTTPS)
                ? new RemoteAddress(RemoteKind.HTTPS, address)
                : new RemoteAddress(RemoteKind.FOLDER, folder(address).orElseThrow().toString());
    }

    private static Optional<AddressProblem> withScheme(String address) {

        String scheme = address.substring(0, address.indexOf(':')).toLowerCase(Locale.ROOT);
        return switch (scheme) {
            case HTTPS -> https(address);
            case "http" -> Optional.of(AddressProblem.NOT_SECURE);
            case "git" -> Optional.of(AddressProblem.GIT_PROTOCOL);
            case "ssh", "git+ssh", "ssh+git" -> Optional.of(AddressProblem.SSH);
            case FILE -> folder(address).isPresent() ? Optional.empty() : Optional.of(AddressProblem.UNKNOWN);
            default -> Optional.of(AddressProblem.UNKNOWN);
        };
    }

    /** The problem of an {@code https://} address: none, unless it has no host or carries credentials. */
    private static Optional<AddressProblem> https(String address) {

        try {
            URI uri = new URI(address);
            if (uri.getRawUserInfo() != null)
                return Optional.of(AddressProblem.CREDENTIALS_IN_ADDRESS);

            return uri.getHost() == null ? Optional.of(AddressProblem.UNKNOWN) : Optional.empty();
        } catch (URISyntaxException e) {
            return Optional.of(AddressProblem.UNKNOWN);
        }
    }

    /** The folder {@code address} names, as a full path or a {@code file:} address; empty if it names none. */
    private static Optional<Path> folder(String address) {

        try {
            Path path = hasScheme(address, FILE) ? Path.of(new URI(address)) : Path.of(address);
            return path.isAbsolute() ? Optional.of(path.normalize()) : Optional.empty();
        } catch (URISyntaxException | IllegalArgumentException e) {
            // IllegalArgumentException covers InvalidPathException too, and a file: address that is no path.
            return Optional.empty();
        }
    }

    /** True if {@code address} starts with {@code scheme} and a colon, in any case. */
    private static boolean hasScheme(String address, String scheme) {

        return address.regionMatches(true, 0, scheme + ":", 0, scheme.length() + 1);
    }
}
