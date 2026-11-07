package it.infog.psiche.settings.model;

/**
 * What a book's remote repository asks for over HTTPS: a username and a personal access token, kept in the
 * {@code Keychain}, never in the book.
 *
 * @param username the account's name on the host
 * @param token    the personal access token, which stands for the account's password
 */
public record Credentials(String username,
                          String token) {

    /** Names the username only, so a log or a message that prints credentials never shows the token. */
    @Override
    public String toString() {

        return "Credentials[username=" + username + ", token=hidden]";
    }
}
