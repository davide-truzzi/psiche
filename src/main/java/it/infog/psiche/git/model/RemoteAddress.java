package it.infog.psiche.git.model;

import it.infog.psiche.git.model.enums.RemoteKind;

/**
 * The address of a book's remote repository, as checked by {@code RemoteAddresses}.
 *
 * @param kind    on a host over HTTPS, or in a folder
 * @param address the {@code https://} address, or the folder's absolute path
 */
public record RemoteAddress(RemoteKind kind,
                            String address) {
}
