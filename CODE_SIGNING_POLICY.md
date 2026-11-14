# Code signing policy

Free code signing provided by [SignPath.io](https://about.signpath.io), certificate by
[SignPath Foundation](https://signpath.org).

## What is signed

Only Psiche Writer's own Windows program files, built from this repository's source by the
[Package workflow](.github/workflows/package.yml) on GitHub Actions. Nothing built on a personal computer is signed.
The libraries Psiche ships with (JavaFX, the Java runtime and others) are signed by their makers or not at all; their
licences are listed in [README_DETAILED.md](README_DETAILED.md#licence).

Every release is signed only after a person on the team approves it by hand.

## Team roles

- **Committers and reviewers:** [Davide Truzzi](https://github.com/davide-truzzi)
- **Approvers:** [Davide Truzzi](https://github.com/davide-truzzi)

Changes from anyone outside the team are reviewed by a reviewer before they are merged.

## Privacy

This program will not transfer any information to other networked systems unless specifically requested by the user.

Psiche connects to the network only when the author asks it to:

- **Synchronize** sends the book to the Git repository the author set in Book properties, and fetches it from there.
  Books have no repository until the author adds one.
- Clicking a web link (in the preview, in About, or the Buy Me a Coffee button) opens it in the system's browser.

Spell checking, exports and the writing history all work offline, on the author's own computer.

## Uninstalling

Psiche installs nothing. To remove it, delete the folder you unzipped (Windows) or the `psiche.AppImage` file
(Linux). Its settings live in `%APPDATA%\psiche` on Windows and `~/.config/psiche` on Linux; delete that folder too to
remove them. Your books stay wherever you saved them.
