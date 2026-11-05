# Changelog

## Unreleased

### What is new

- **Repetitions**: select some text, or rest the caret in a word of four letters or more, and its other places in
  view show on a faint red, so a word used twice close together stands out while you write, not only when you read
  the chapter again. It finds exactly what you select, capitals or not, inside longer words too ("meraviglios" finds
  "Meravigliosa"); short words such as "il" or "the" are left alone. The status bar says how many times the word is
  in the chapter. **Highlight repetitions** in Preferences turns it off.
- **Book counts…**, under the chapter list: the whole book's characters, words and pages, counted as the status bar
  counts a chapter, from the chapters as saved. Click it to fold the numbers away; Psiche remembers.
- **Move to chapter…** in a note's right-click menu moves the note, with its file, straight to another chapter's
  board, where it keeps its place and lies on top.
- The words you add to the dictionary are suggested too: a name of your own typed wrong now gets the right spelling
  first in its right-click menu.
- The status bar's counts are no longer cut short by a long message beside them.
- **Close book**, in the Book menu, closes the open book and shows the welcome page again, asking first about unsaved
  changes.
- The window's title shows Psiche's version, as "Psiche Writer 0.3.0 Alpha", until the first stable release.
- **Export Psiche project** and **Import Psiche project**, in the Book menu, move a whole book to another computer, or
  keep a copy of it, as one `.psiche` file: chapters, media, notes with their files, and the Git history. The book is
  frozen while it is exported, with a bar of blocks in the status bar showing how far it has come. An import over a
  folder of the same name asks whether to back it up first, and opens the book when it is done.

### For testers

- Repetitions: tell us if the highlight ever gets in the way while you write, or if the text moves when it appears.
- Psiche projects: try moving a book between Linux, Windows and macOS, and tell us if anything is missing on arrival.

## 0.3.0 Alpha (2026-11-03)

The third release for testing: spell checking in six languages, export to DOCX, and far less power used while
nothing happens.

### What is new

- **DOCX export**, for a chapter and for the whole book, in **Export**: the same manuscript as the ODT, in Word's own
  styles where it has them, so its navigator lists the headings. The book's contents link to each chapter but show no
  page numbers: Word would only work them out after asking the reader.
- A word longer than the line, such as names pasted without their spaces, is broken where the line ends: the preview
  scrolled sideways, and the PDF cut it off at the edge of the page.
- Psiche uses far less power while you read or think: with a chapter open and nothing happening, it kept a processor
  core fully busy, redrawing the window hundreds of times a second for the comments' dots; now it takes a few per
  cent. The caret blinks for ten seconds after you last type or click, then stays shown, as in GTK and GNOME; and
  the preview, after a pause in typing, changes its text in place instead of loading the whole page again.
- **Spell checking**, offline, in the book's language: English, Italian, French, Spanish, German, and Portuguese
  from Brazil and from Portugal. A word spelt wrong gets a dotted red line under it and a red dot in the margin,
  beside a comment's dot if its line has one, with red dots at the top and bottom of the page counting those beyond
  the view, which a click goes to. Right-click one for suggestions, or for **Add to dictionary** or **Ignore**, which
  make it right in every book. Words with a capital letter, as names have, are left out unless **Strict spell check**
  is on in Preferences, where **Check spelling** turns it all off. Book properties and Preferences show a green tick
  after a language Psiche has a dictionary for.
- The packages offer every language in **Book properties** and **Preferences**: their Java runtime only knew English.
- Dates read the same in every language, as DD/MM/YYYY with a 24-hour time: the History tab, past versions, Deleted
  chapters and Detached notes.
- The preview no longer leaves a hidden folder named after the app, `.it.infog.psiche.ui.WriterApp`, in the home
  folder: its web engine's data now goes in the settings folder, as `webview`.

### For testers

- The DOCX export was checked in LibreOffice: tell us how a chapter and a whole book open in Word, and whether
  anything looks different from the ODT.
- Spell checking: tell us about words marked wrong that are right, especially in Italian, French, Spanish, German
  and Portuguese, and whether a long chapter with many mistakes still scrolls smoothly.
- On a laptop, the battery should now last much longer with Psiche open; tell us if it doesn't.

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
