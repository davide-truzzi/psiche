package it.infog.psiche.git.model.enums;

/** Where a book's remote repository is: on a host reached over HTTPS, or in a folder of this computer. */
public enum RemoteKind {

    // GitHub, GitLab, Gitea and the like, with a username and a personal access token.
    HTTPS,
    // A USB stick, an external disk or a network share.
    FOLDER
}
