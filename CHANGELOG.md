# Changelog

## 0.6.0 Alpha (not released yet)

### What is new

- **Repetitions while you type**: the word being written now gets its other places boxed in red at each key, from
  its second letter, in the four paragraphs around it (its own, the two before and the one after, as far as they are
  in view), so a fast writer sees a repetition without stopping. Once you pause, the faint red of the whole chapter's
  search and its count in the status bar take their place, as before. **Highlight repetitions** in Preferences turns
  both off.
- **Repetitions while you scroll**: the places of the word you rest on, or select, now show as soon as they come into
  view, however fast you scroll, instead of a moment after you stop: easier to find them all, as the status bar
  counts them.
- **Find and replace**: **Find…** in the editor's right-click menu, or Ctrl+F (⌘F on macOS), opens a bar under the
  text, hidden until then, with the selection or the word at the caret. Enter and Shift+Enter, or F3 and Shift+F3
  (⌘G on macOS), go from place to place round the chapter, `3 of 17` telling where you are; **Aa** makes capitals
  count. A line break can be looked for too: it shows as ↵, and Ctrl+Enter types one. A link's text and an image's
  caption are searched, never their address or file. The arrow at the bar's left, or Ctrl+H (⌘⌥F on macOS), shows
  **Replace** and **Replace all**, each a single step for Undo. The search is in the open chapter.
- **Extended Markdown**: strikethrough `~~gone~~`, subscript `H~2~O`, superscript `X^2^` and footnotes, `text[^1]`
  with `[^1]: The note.` anywhere in the chapter, as the Markdown Guide writes them; README's new **Writing in
  Markdown** lists everything Psiche reads.
  - The right-click menu has **Strikethrough** `Ctrl+Shift+X`, **Subscript** `Ctrl+=` and **Superscript** `Ctrl++`,
    and **Clear formatting** removes them too. **Insert ▸ Footnote** is `Ctrl+Alt+F` (`Cmd+Option+Shift+F` on macOS).
  - Footnotes show in amber in the editor, a wrong one in orange: a reference to no note, a note nothing refers to, a
    second note of a label. `Ctrl+click` goes from a reference to its note and back.
  - In Default mode a footnote never breaks: references and whole notes are blocks, a note is written and changed in
    a dialog that indents its paragraphs for you, which can also refer to a note the chapter has. Removing a note's
    last reference removes the note, removing a note removes its references, and Cut, Copy and Paste carry the note
    with the text. Wrong ones show their fix in the right-click menu.
  - The preview raises the references and lists the notes at the chapter's end, a click going to the note in the
    editor and back. The PDF, ODT and DOCX print them at the foot of the page, the ODT and DOCX as LibreOffice's and
    Word's own; the EPUB after each chapter, as pop-ups. Notes are numbered from 1 in every chapter.
  - The counts and the spelling take in the notes' text, not their labels; the find bar finds the labels too.
- The editor's right-click menu is shorter: the lists are under **List**, images and links under **Insert**, and
  **Modify image…**, **Modify link…** and **Remove link** show only on an image or a link.
- **Pinned notes**: a chapter's note can be pinned to the paragraph it is about. **Pin to current paragraph**, in the
  note's right-click menu, pins it to the paragraph of the editor's caret. A pin then shows in the editor's right
  margin beside the paragraph, the note's first line as its tooltip; a click on it shows the note in the middle of
  the Notes tab, with a thick brass border until you type. A pinned note has a pin among its toolbar's icons, and
  **Go to paragraph** and **Unpin** in its menu.
  - A paragraph holds one note, and a note one paragraph: pinning a note where another is pinned asks first.
  - Pins follow their paragraphs as you write. A pinned paragraph joined to another pinned one, emptied or deleted
    sends its note to the nearest free paragraph, as the status bar says, and Undo brings it back. After a Revert or a
    restore from History, pins are found again by their paragraphs' first words; a pin whose paragraph is gone is
    let go, the note staying on the board.
  - Moving a note to another chapter or to the book, or detaching it, unpins it. Pins travel with Export notes and
    Import notes, and in a Psiche project.

### What is fixed

- **Opening a book**: choosing a folder or a file inside a book, such as its `chapters` folder or a chapter, now
  opens that book, instead of failing because it was not the book's own folder. Anything else says "This is not a
  Psiche book", with what a book's folder is, and **Choose again**. While no book is open, a book's folder, or
  anything inside it, dropped on the window opens it too.
- **Open recent**: a book that is not found, as one moved elsewhere, now says where it was looked for, and a cross
  beside it takes it alone off the list; before, only **Clear list** could, with all the others.
- **Right-click menus** now open as you release the button, as on Windows. Dragging the editor's scroll bar right
  after choosing an item from a menu used to select the text, or move the split between the editor and the tabs,
  instead.

## 0.5.1 Alpha (2026-11-08)

0.5.0 Alpha with fixes for saving on Windows.

### What is fixed

- **Preferences that were not kept on Windows**: on some computers, a program that briefly opens each file just
  written, such as the virus scanner, the search indexer or OneDrive, could keep the settings from being read or saved.
  Psiche now waits a moment and tries again, for the settings and for chapters, notes, images and the keychain alike.
- A settings file that could not be read for a moment no longer loses the preferences: the next change, such as where
  the window was, used to save over it with that change alone. Where each chapter was left is kept the same way.
- When the preferences really cannot be saved, the status bar says so, instead of "Preferences saved"; they then last
  until Psiche Writer closes.

### For testers

- Psiche run from its package now keeps a log, `psiche.log`, in its settings folder (on Windows `%APPDATA%\psiche`),
  and the one of the run before as `psiche.previous.log`. If something goes wrong, send both along with the report.

## 0.5.0 Alpha (2026-11-07)

The fifth release for testing: images and links in the chapters, a Default editor mode where the Markdown recedes,
and, for advanced use, a book kept in step with a remote repository.

### What is new

- **Images**: **Insert image…**, in the editor's right-click menu, puts a JPEG or PNG image into the chapter after the
  caret's line, with a caption, which the book prints under it. The image is copied into the book's
  `media/<chapter>/` folder, and committed with the book; the dialog warns when it will look poor in the exports.
  The preview and every export show it centred, with its caption.
- **Media** tab, beside the preview: the chapter's images, with a preview of each. Those its text does not show, and
  those of low quality, are marked; **Insert at the caret** puts one back, and **Detach** sets one aside, to bring
  back from **Detached images**. Renaming or deleting a chapter takes its images along, as it does its notes.
- An image whose file is missing shows in orange in the editor, and as a box in the preview.
- **Links**: **Insert link…** in the right-click menu, or Ctrl+K (⌘K on macOS), links the selection to a web address
  or to another chapter, or to a heading in it. On a link it reads **Modify link…**, and **Remove link** keeps its
  text. Links show in teal in the editor; one that leads nowhere, to a missing chapter or heading or to a web address
  that is not one, in orange. A link typed by hand, `[text](address)`, works the same.
- Renaming a chapter rewrites the links to it in every chapter; deleting one leaves them flagged, and restoring it
  heals them.
- A click on a link to a chapter in the preview opens that chapter. In the exports, links lead to their chapter or
  heading; one whose chapter is not in the export stays as plain text.
- The ODT's contents no longer show "?" for page numbers when the file is opened: each line is the chapter's title,
  linked to it, as in the DOCX.
- **Editor mode**, in Preferences: **Default** or **Advanced**. The text is the same in both, character for
  character; only the way the editor shows it changes, at once, with no need to reopen the chapter.
  - **Default**, where every install starts, lets the Markdown recede: a link's `[` and `](address)`, an image's `![`
    and `](file)`, a comment's `<!--` and `-->` are faded in their element's colour. A link sits on a faint teal tint,
    an image on a faint green one, a comment on a faint grey; a link that leads nowhere and a missing image on a
    faint orange. Links and images are blocks: typing inside one does nothing, the caret steps over it, and Backspace
    right after one or Delete right before one removes it whole, which Undo brings back. They are changed from the
    right-click menu, **Modify link…** and **Modify image…**, which act on the one under the pointer. A link typed by
    hand becomes a block as soon as its `)` closes it.
  - **Advanced** is the editor as it was: the Markdown as written, free to edit anywhere.
- **Modify image…**, in the right-click menu on an image, in both modes: the image's dialog again, with its caption
  to change. **Choose another** swaps the file and keeps the caption, and repairs an image whose file is missing; the
  old file stays in the chapter's media folder, to keep or detach.
- Images show in sage green in the editor, in both modes, set apart from the text and from the links' teal.
- Repetitions leave out image lines and links' addresses: a word there is neither highlighted nor counted. A link's
  text still counts, as prose.
- **Remote repository**, for advanced use, in Book properties: a book can be connected to a repository on a host
  such as GitHub or GitLab, over HTTPS with a username and a personal access token, or to a folder, such as one on a
  USB stick or another drive, where Psiche keeps it in a `PsicheRepository` folder. **Test** checks the address, the
  token and its right to write, and what the repository holds, before Save. A repository created with a README, a
  licence or a .gitignore is refused, saying how to make one Psiche can use. Most authors need none of this: the
  book's own history and the Psiche project export are enough.
- **Synchronize**, beside Commit, only for a book with a remote, in place of Push: it brings in the revisions made on
  another device, merges them with the book's, and sends the book's, in one go. Work not committed waits for a
  commit first. When both devices changed the same lines of a chapter, the book keeps this device's text and the
  other's is added as a chapter of its own, "<title> (other device, YYYY-MM-DD)", to compare and then delete.
- **Synchronize automatically**, a switch in the Remote repository section, for each book on each device. On, the
  book synchronizes as it opens, if the remote has something new, after every commit, and before it closes, asking
  to commit any work not committed yet. Off, nothing happens until you click Synchronize. A remote that cannot be
  reached, offline or on a drive not plugged in, is told in the status bar and never stops a book opening or closing.
- The tokens are kept in Psiche's own **keychain**, a file in its settings folder locked by a master password, which
  Psiche asks once a session, when a token is first needed. It cannot be recovered: **Reset keychain** deletes every
  token, to be made again on the host. Tokens are never written in the book, nor in a Psiche project. `~/.netrc` is
  no longer read.
- A book's images are committed with it, so they have a history and travel with Synchronize; notes stay out of Git.
  A book opened for the first time with this version commits its images on its own.
- A second device gets a book by importing a Psiche project exported on the first, then typing the token once.
- Images whose chapter has a space in its name were left out of the ODT, DOCX and EPUB exports, and an image written
  with `%20` in its path showed as missing in the editor. Both work now.

### For testers

- Default mode: tell us if a link or an image ever refuses an edit you expected to make, or if the caret lands
  somewhere unexpected around one.
- Remote repository: tell us if Synchronize ever leaves a chapter different from what you expected on either device.
  Keep a Psiche project export of the book while you try it.

## 0.4.1 Alpha (2026-11-06)

0.4.0 Alpha with one addition for Windows 11.

### What is new

- **Windows 11 Smart App Control**: on some computers it blocks `psiche.exe`, since this test release is not yet
  digitally signed, and offers no way to run it anyway. The `psiche` folder now holds `psiche.cmd` too: double-click it
  instead, and Psiche starts through the Java runtime it brings, which is signed. Settings, books and notes are the
  same either way. `ISTRUZIONI_WINDOWS_11.txt` beside it explains the steps in Italian. Signed packages, which need
  neither, are planned for the first stable release.

## 0.4.0 Alpha (2026-11-05)

The fourth release for testing: notes for the whole book on a board of their own, repeated words shown while you
write, the whole book's counts, chapters that reopen where you left them, and whole books moved or kept as one
Psiche project file.

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
- When **New book** cannot create a book, it says why, for example that Psiche has no permission to write in that
  folder, and suggests one of your own, such as Documents. Its folder chooser starts in your home folder rather than
  the folder Psiche is installed in.
- Pin the tab that shows when a book opens: right-click **Preview**, **Notes** or **History** and tick **Show when a
  book opens**. The preview stays the default.
- **Book notes**: notes for the whole book, such as a map, the cast or a timeline, on a board of their own in the
  Notes tab. A switch above the board, **01-departure | Lorem**, after a page and a book, chooses the chapter's board
  or the book's; the tab reads **Notes (3+2)**, the chapter's notes then the book's. Each book reopens on the board you
  looked at last. Drag a note onto the other half of the switch to move it there, or use **Move to Lorem** /
  **Move to 01-departure** in its menu.
- A chapter reopens where you left it: with the caret where it was and the same paragraph at the top, after switching
  to another chapter, closing the book or restarting Psiche. Psiche keeps this in `psiche.properties` in the book's
  folder, which every book gets as it opens, and keeps that file out of Git with a commit of its own.

### For testers

- Repetitions: tell us if the highlight ever gets in the way while you write, or if the text moves when it appears.
- Psiche projects: try moving a book between Linux, Windows and macOS, and tell us if anything is missing on arrival.
- Book notes: tell us whether dragging a note onto the other half of the switch feels natural, and whether the
  Notes (3+2) count reads clearly.

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
