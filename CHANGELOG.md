# Changelog

## Unreleased

- The preview no longer leaves a hidden folder named after the app, `.it.infog.psiche.ui.WriterApp`, in the home
  folder: its web engine's data now goes in the settings folder, as `webview`.

## 0.2.0 Alpha (2026-11-02)

The second release for testing. The manuscript editor is new underneath: it looks and works as before, and now it can
colour what the text holds, starting with its comments.

### What is new

- A dot before each chapter's name in the list shows whether it changed since the last revision: green if not, red
  once it has changes for the next one. Pointing at it says so in words.
- The editor shows HTML comments (`<!-- a note -->`) in grey: those the preview and the exports leave out. A brass dot
  in the margin marks each line with one, and dots at the top and bottom of the page tell of comments beyond the view;
  a click on one goes to the next.
- The preview follows the editor through long paragraphs and to the end of the chapter.
- **Preferences** sets the editor's and the preview's font size, each on its own. **Apply** puts a change into effect
  without closing it, and **Revert** goes back to the values Preferences opened with, as **Cancel** does before closing.
- Deleted notes is now **Detached notes**, as it also holds notes that are not deleted, such as those imported for a
  chapter the book doesn't have. Their cause reads "no such chapter in this book".
- Inside, the largest classes are split along what they do, and the Gradle project is named `psiche-writer`, as the
  app's own jar now is.

### For testers

- The editor is a RichTextFX text area instead of JavaFX's own. Typing, selecting, Undo, the menus and the shortcuts
  should feel the same; tell us if they don't. Worth a try in particular: accents and dead keys on your keyboard,
  pasting from a word processor or a browser (plain text should arrive), and scrolling a long chapter by dragging the
  scroll bar.

## 0.1.0 Alpha (2026-11-01)

The first release for testing. Everything below works, but it is new: keep a copy of your books while you try it.

### What it does

- **Books**: create or open a book, a folder with its chapters as Markdown files, its media, and its notes; edit its
  properties (title, subtitle, authors, language, rights, cover); reopen recent books.
- **Writing**: a manuscript editor in Courier Prime with formatting from the right-click menu or the keyboard (bold,
  italic, headings, lists, quotes, comments, clear formatting), unsaved changes kept per chapter until saved, and the
  characters, words and pages of the open chapter in the status bar.
- **Chapters**: create, rename and delete them, each change committed on its own; restore a deleted chapter.
- **Preview**: the chapter typeset as it is written, following the caret, in a paper or a dark theme.
- **Notes**: a board of post-it notes for each chapter, with colours, a file on each note (pictures of images and
  PDFs), Deleted notes to restore or remove for good, and Export notes / Import notes to move them between computers.
  Notes stay out of Git.
- **History**: Git commit and push from the toolbar; each chapter's earlier versions, with the changes shown word by
  word, restored as an undoable edit.
- **Export**: a chapter to a manuscript PDF or ODT, or to Markdown; the whole book to a manuscript PDF or ODT, or to
  EPUB, for stores or for Kindle.
- **Preferences**: reopen the last book at startup, the preview's theme, and the language and author of new books.
- **Packages**: `psiche.AppImage` for Linux and a portable zip for Windows, each with its own Java runtime, so
  nothing needs installing.

### For testers

- Psiche Writer keeps its settings in `~/.config/psiche` on Linux and in `%APPDATA%\psiche` on Windows; a
  `psiche.AppImage.config` folder next to the AppImage, or a `settings` folder next to `psiche.exe`, keeps them with
  the app instead.
- While a book is open, change its chapter files only in Psiche Writer: the editor does not reload a chapter changed
  elsewhere.
- Notes are not in Git: Export notes is their backup.
- What is not there yet is listed under [Limitations](README.md#limitations) in the README.
