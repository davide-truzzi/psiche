# Psiche Writer

A desktop editor for writing books in Markdown. Each book is a plain folder with its own Git repository. You write
chapters next to a live, typeset preview, record revisions with Git, and look back at or restore any earlier version
of a chapter, all without leaving the editor.

## Purpose

Psiche Writer is for authors who want their manuscript in open, plain-text files instead of a proprietary format, and who
want version history without learning the Git command line. A book stays an ordinary folder. You can edit it with any
other tool while Psiche Writer is closed, sync it with any Git host and read it without Psiche Writer.

## Status

**0.2.0 Alpha**, the second release for testing. Psiche Writer does what a book needs from first draft to export:
create or open a book, write its chapters in Markdown with a typeset preview beside them, keep notes on each
chapter's board, commit and push with Git, browse or restore earlier versions, and export the book to PDF, ODT or
EPUB. It is not finished: keep a copy of your books, read [Limitations](#limitations), and expect things to change
before 1.0. What this release holds is in [CHANGELOG.md](CHANGELOG.md).

## Book layout

```
my-book/              ← the folder; the book's title is set in its properties
├── .git/             ← the book's own Git repository
├── chapters/         ← one Markdown file per chapter, ordered by file name
│   ├── 01-departure.md
│   └── 02-the-crossing.md
├── media/            ← what the book needs besides its text
│   ├── book.properties   ← title, subtitle, authors, language, copyright, identifier
│   ├── cover.jpg         ← or cover.png, set from Book properties…
│   └── style.css         ← the ebook's stylesheet, a default you can edit
└── notes/            ← the chapters' notes, kept out of Git; only once a chapter has notes
    └── 01-departure/     ← a chapter's notes, named like its file
        └── 20261101-093000-a1b2/   ← one folder per note: note.md, note.properties, its file
```

- Only `*.md` files directly inside `chapters/` count as chapters. Use prefixes such as `01-`, `02-` to set the order.
- A chapter's notes follow it when it is renamed in Psiche Writer. When it is deleted, they move to a folder named after
  it and the time, such as `01-departure-del-2026-11-01-09-30-00`, which frees the name for a new chapter; **Detached
  notes** brings them back (see [Notes](#notes)).

> [!WARNING]
> While a book is open in Psiche Writer, don't change its chapter files with another program: another editor, a
> `git pull`, a sync client such as Dropbox, or a script. Psiche Writer keeps each chapter you work on in memory and
> writes it back when you save, so it can overwrite a change made elsewhere, or bring back a chapter deleted
> elsewhere, and the result is hard to predict. Close the book first, or close Psiche Writer. Adding or removing
> whole chapter files is the exception: the chapter list follows those (see [Books](#books)).

## Features

### Books
- The **Book** button in the toolbar opens a menu of what acts on the whole book: **New book**, **Open book**,
  **Open recent**, **Save chapters**, **Book properties**, **Export**, **Export notes** and **Import notes**. What acts
  on one chapter is in that chapter's right-click menu.
- **New book** asks for a title and a location. It creates `<title>/chapters/` and `<title>/media/` with the book's
  properties, in the language and with the author chosen for new books in **Preferences**, and the default ebook
  stylesheet, initialises a Git repository on branch `main`, and opens the book. It refuses titles that aren't a single
  folder name and never overwrites an existing folder.
- **Open book** opens any folder that contains both `chapters/` and `media/`. A book folder can also be passed as the
  first command-line argument.
- **Open recent** lists the last 10 books opened, by their titles, the open one left out; two books of the same title
  show the folder they are in. A book whose folder is gone shows greyed out as *(not found)*, since it may be on a
  drive that is not plugged in, until **Clear list**. With no book open, the folder dialogs start next to the latest
  book.
- **Preferences**, next to **Book** in the toolbar, holds what applies to the app rather than to one book, in two
  groups. Nothing changes until you click **Apply**, which keeps Preferences open so you can try a size or a theme and
  see it at once, or **Save**, which closes it. **Revert** puts every field back as it was when Preferences opened,
  and undoes what you applied since; **Cancel**, or closing Preferences, does the same and closes it:
  - **General**
    - **Startup**: reopen the last book when Psiche Writer starts; off until you turn it on. A book given on the
      command line opens instead, and a last book that is gone is skipped.
    - **Editor font size**: the size of the manuscript's text, from 12 to 32 px, 16 px until you change it. The space
      between lines grows and shrinks with it.
    - **Preview theme**: **Default**, the page of paper, or **Dark**, light ink on dark paper. The chapter shown
      renders again in the new theme at once.
    - **Preview font size**: the size of the preview's text, from 12 to 32 px, 16 px until you change it; headings,
      margins and the drop cap follow it. The chapter shown renders again, lined up with the editor.
    - Exports are not affected by any of these.
  - **New book defaults**
    - **Language** and **Author**: what a new book starts with, English (United States) and no author until you
      choose others. A book's own language and authors stay in **Book properties**.
- **About Psiche**, the orange button at the bottom left of **Preferences**, opens the About dialog over it: the logo,
  the version, the author, the licence (GNU GPL 3 or later, readable in the app), a yellow **Buy me a coffee** button to
  support the project, and the licences of the fonts and libraries Psiche Writer ships with. The fonts' licences open in
  the app, the others on their publishers' sites.
- The language, in **Preferences** and **Book properties**, can be searched: typing narrows the list to the languages
  whose name or tag contains what you typed, ignoring accents (`ital`, `bokmal`, `pt-br`); the arrow keys and Enter
  pick one, Escape keeps the one before.
- The window opens where it was when you last closed it, maximized if it was. If it would be off every screen, for
  example after a monitor is unplugged, it opens at its default size instead.
- The settings (the recent books, the preferences and where the window was) live in `settings.properties`, never
  inside the app, in a folder that also holds `webview`, where the preview's web engine keeps its own data:
  - Linux: `$XDG_CONFIG_HOME/psiche`, by default `~/.config/psiche`. A `psiche.AppImage.config` folder next to the
    AppImage moves them there, as AppImage's portable mode does for any app.
  - Windows: `%APPDATA%\psiche`. macOS: `~/Library/Application Support/psiche`.
  - Portable: a `settings` folder made next to the app's launcher is used instead, and books on the same drive are
    remembered relative to it, so a USB stick works whatever its drive letter.
  - The system property `psiche.settings.dir` names another folder, as for development.
- **Book properties** edits what a future ebook needs besides the chapters, in a simple form:
  - title (required) and subtitle, shown in the toolbar instead of the folder name;
  - authors, one per line;
  - language, chosen by name and stored as a tag such as `it-IT`;
  - copyright, filled in as "© year first author" until you write your own.

  The cover is chosen in the same form. Any JPEG or PNG image is copied into `media/` as `cover.jpg` or `cover.png`,
  replacing the previous cover, and **Remove** deletes it. Nothing is written until **Save**. The properties go to
  `media/book.properties`, a plain `key=value` file that also keeps a unique identifier for the ebook, generated
  once. Saving adds the default `media/style.css` if the book has none, and never overwrites an existing one.
- **New chapter** above the chapter list asks for a title, suggesting the next number prefix (`03-` when the book
  has two chapters). It creates an empty `chapters/<title>.md` and opens it, and refuses a title that clashes with an
  existing chapter. If an earlier chapter had that name, deleted or renamed since, it warns first and asks before
  creating it (see [Limitations](#limitations)).
- The chapter list refreshes automatically when chapter files are added or removed outside the editor (for example
  by a `git pull` or a file manager).
- Right-click a chapter for **Rename** and **Delete**; right-clicking does not open it. Both need the book's Git
  repository, and everything saved and committed first: each is then recorded as a commit of its own, with a message
  such as `Rename chapter "01-alba" to "01-prologo"` or `Delete chapter "03-baba"`. That commit holds only the
  chapter's own file, so files Git does not track yet, such as an editor's backup file (`01-alba.md~`), neither
  stand in the way nor slip into it.
  - A renamed chapter keeps its whole history, since a commit that only renames a file is always a rename for Git.
  - **Delete** asks for confirmation. **Deleted chapters** below the chapter list lists the chapters deleted from
    the book, newest first, and **Restore** (or a double-click, or Enter) brings one back with the text it had when
    it was deleted, as a `Restore chapter "03-baba"` commit. Its history goes on from before the deletion.
  - The deleted chapters are read from the Git history, so chapters deleted outside the editor, once committed, are
    listed too. A chapter whose name is in use again, as when a new chapter took it, is not listed.
- **Export** in a chapter's right-click menu writes the chapter, as it is saved, to a file you pick (by default
  next to the book's folder, not inside it, where it would be one more change to commit):
  - **PDF**: a manuscript to send to an editor. A4, 12pt Libertinus Serif (embedded), double-spaced, indented
    paragraphs, the authors, the book's title and the page number at the top of every page, the Psiche mark in black
    under the chapter's title, and `* * *` for scene breaks. Raw HTML in the chapter is printed as text, and links to
    the book's own files keep only their text. HTML comments (`<!-- a note -->`) are notes to yourself: the PDF and ODT
    leave them out.
  - **ODT**: the same manuscript as an OpenDocument text file, for an editor to work on in LibreOffice, Word or
    any other word processor. The text is set in named styles (*Text Body*, *First Paragraph*, *Heading 1*,
    *Quotations*, *Scene Break*…), so restyling one changes the whole chapter. It names Liberation Serif without
    embedding it, so where that font is missing the word processor's own serif takes its place. Images are packed
    into the file; web and mail links stay links, links to the book's own files keep only their text.
  - **Markdown**: the chapter's file as it is. Image links still point to `../media/`, so images show only next
    to the book's media folder.
- **Export** in the Book menu writes the whole book, as it is saved, to one file you pick (by default named after
  the book and next to its folder):
  - **PDF**: the chapter manuscript for the whole book. A title page with the cover, title, subtitle and authors,
    then the contents (*Indice*, *Contents*… in the book's language), both without the page header, then every
    chapter in the order of the chapter list, each starting on a new page however short it is. Pages are numbered
    from the first chapter. Each line of the contents links to its chapter and shows, past dot leaders, the page it
    starts on; the contents are also the PDF's bookmarks, which PDF readers list beside the pages.
  - **ODT**: the same book as an OpenDocument text file, in the named styles of a chapter's ODT, plus *Cover*,
    *Title*, *Subtitle* and *Book Authors* for the title page, which has a page style of its own (*Front Matter*)
    without the header. The contents follow on a page of the same style, in LibreOffice's own table of contents
    styles (*Contents Heading*, *Contents 1*, *Index Link*). Each line links to its chapter's bookmark (`chapter-1`,
    …) and holds a page field, which the word processor works out as it lays the book out, and again when the text
    changes (in Word, update the fields with F9).
  - **EPUB**: an EPUB 3 ready for Apple Books, Kobo, Google Play Books and distributors. It reads: the cover, full
    screen; the title page (title, subtitle, authors); the contents, a link per chapter; the chapters, a page each, set
    by the book's own `media/style.css`, with the Psiche mark, a red Ψ hidden from screen readers, under each title; and
    the copyright notice from Book properties at the back, so a store's free sample opens on as much of the book as it
    can. It also holds the navigation stores and e-readers use (landmarks, and `toc.ncx` for EPUB 2 readers), the book's
    metadata, and the accessibility metadata of EPUB Accessibility 1.1 (how the book can be read, what helps, no
    hazards), which the European Accessibility Act expects. Images are packed in, shrunk to stay within 4 million
    pixels; fonts are not embedded. Every EPUB the tests export passes EPUBCheck, the validator stores run.
  - **EPUB for Kindle**: the same EPUB without the cover, which KDP adds from the one uploaded with the book's
    details; with a cover inside too, Kindle would show it twice. The file is also smaller, and Kindle charges for
    delivery by size.
  - Before an EPUB, Psiche Writer lists what stores may object to, and you can export anyway or cancel to fix it: no
    cover, or one under 1400 pixels on its short side (Amazon recommends 1600 × 2560), images it has to leave out,
    images without a description (`![description](image)`), or an identifier that is not a valid UUID.
  - Unsaved changes in any chapter are settled first. If some chapters are empty (nothing but blank lines), Psiche Writer
    lists them and asks whether to leave them out or export each as a page showing only its file name, to see where
    it goes in an unfinished book.

### Writing
- Typewriter-style Markdown editor, with a live preview that refreshes 500 ms after you stop typing and keeps its
  scroll position.
- The preview keeps to the same part of the chapter as the editor. While you write, it follows the caret, even through
  a long paragraph, moving only when the caret's place would leave the middle of the preview. When you scroll the
  editor to read, it lines up with the text a third of the way down the editor, until you click or type again.
- While the History tab is open, the hidden preview neither renders nor scrolls. It catches up with your latest text
  and position as soon as you switch back to it.
- The preview is styled as a printed novel: Libertinus Serif, indented paragraphs, the Psiche mark under the
  chapter heading, a drop cap after it and ornamental scene breaks (`---`). Courier Prime is used for the editor.
  **Preferences** offers it in a **Dark** theme too, the same page in light ink on dark paper.
- Clicking a web (`https://…`) or mail (`mailto:…`) link in the preview opens it in your default browser or mail
  app. Other links, such as one to a chapter file, do nothing, so the preview never leaves the chapter.
- A line break inside a paragraph is kept as a line break in the preview, so verse and dialogue can be laid out
  line by line.
- The editor and the preview use flat backgrounds without shadows or textures, because those effects made the
  preview take seconds to redraw when resizing the window.
- Right-click in the editor for Markdown formatting, each with its shortcut (`Cmd` on macOS):
  - **Bold** `Ctrl+B` and **Italic** `Ctrl+I` wrap the selected text, line by line. Text that is already bold or
    italic is not marked twice: a selection overlapping it joins it, so bolding `rem ip` in `**lorem** ipsum` gives
    `**lorem ip**sum`.
  - **Heading 1–4** `Ctrl+1`…`Ctrl+4`, **Bulleted list** `Ctrl+Shift+8`, **Numbered list** `Ctrl+Shift+7` and
    **Quote** `Ctrl+Shift+Q` apply to the lines the selection touches, or the caret's line.
  - **Comment** `Ctrl+Shift+C` turns the selection into an HTML comment, `<!-- a note -->`, which the preview, the
    exports and the counts leave out; with nothing selected, the caret's line, or an empty comment on an empty line.
    A selection over several lines takes them whole. With the caret or selection in a comment, it makes it text
    again.
- The editor shows comments in grey, exactly those the preview leaves out: one inside a code span or a code block is
  text, and stays as the rest.
- A small brass dot in the left margin marks each line in view with a comment, following it as the text scrolls; a
  comment that starts above the view gets its dot on its first line in view. While comments lie above or below the
  view, a dot shows in the page's top or bottom margin, with how many in its tooltip: clicking it brings the next one
  that way a third of the way down the editor, with the caret on it.
- The editor checks the spelling in the book's language, offline, with the dictionary Psiche brings for it: English,
  Italian, French, Spanish, German, and Portuguese from Brazil and from Portugal. A book in another country's
  variety uses its language's: Swiss Italian is checked in Italian. A word spelt wrong gets a dotted red line under
  it, and the line a red dot in the margin, beside a comment's brass dot if it has one, with red dots in the top and
  bottom margins for those beyond the view, as for comments. The text is checked a moment after you stop typing;
  only prose is, not code, comments, links' addresses or words with digits. Words that start with a capital letter,
  or have one after an apostrophe, as names do ("Montenevoso", "d'Annunzio"), are left out, so a novel's people and
  places need not be added one by one; **Strict spell check** in Preferences checks them too. Right-click a marked
  word for up to five words you may have meant, which replace it (`Ctrl+Z` brings it back), or for **Add to
  dictionary** or **Ignore**: both make it right in every book, and Psiche keeps such words in `words.txt` in its
  settings folder. **Check spelling**
  there turns it off for every book; Book properties and Preferences show a green tick after a language Psiche has a
  dictionary for.
  - **Clear formatting** `Ctrl+\` removes heading, list and quote markers and the bold or italic the selection
    touches.
  - Each format is a single step for Undo.
- The status bar shows the open chapter's characters (spaces included), words and pages, updated as you type. They
  count the text a reader sees: Markdown markers, link addresses, images and HTML are left out, a line break inside
  a paragraph counts as a space, and a word needs at least one letter or digit, so a dash between spaces is not one.
  Right-click the pages to choose how they are counted:
  - **Standard (300 words)**: words / 300 · **Cartella (IT)**: characters / 1800 · **Feuillet (FR)**: characters / 1500
  - **Manuscript (250 words)**: words / 250 · **Printed paperback**: words / 350

  The choice is kept in `media/book.properties` (`pageCount=…`), so it is part of the book and needs committing like
  any other change. Until one is chosen, Italian books count in cartelle, French ones in feuillets, others in
  standard pages.
- **Save chapters** in the Book menu, or `Ctrl+Shift+S` / `Cmd+Shift+S`, saves every chapter with unsaved changes.
  To save one chapter, open or not, use **Save** in its right-click menu, or `Ctrl+S` / `Cmd+S` for the open one.
  Nothing is written to disk otherwise.
- Switching chapter keeps unsaved changes in memory: the chapter is tagged *unsaved* in the list, and its text is
  restored when you go back to it. The status bar shows whether the open chapter has unsaved changes.
- In a book with Git, a dot before each chapter's name tells whether it changed since the last revision (the last
  commit): green while it is as that revision left it, red once it has changes, saved or not, that the next
  **Commit** will record. A new chapter is red until its first commit. Point at the dot for a word on what it means.
- While any chapter has unsaved changes, Psiche Writer lists them and asks before it acts:
  closing the window, **Open book**, **New book** or **Commit**: *Save all*, *Discard* or *Cancel*.
  So Git always records exactly what is on disk.

  If *Save all* cannot write a chapter, that chapter keeps its unsaved changes, the others are saved, and the action is
  cancelled.

### Notes
- The **Notes** tab, between Preview and History, holds the open chapter's notes, like post-its on a board of 1600 ×
  2500 pixels. The tab reads **Notes (3)** when the chapter has notes, and is disabled with no book open.
- **New note** in the board's corner, or a double-click on an empty spot, adds a note with the caret in it. Type in
  place: the text is saved a second after you stop typing, and when you leave the note.
- Drag a note by its toolbar, the darker strip at its top, to move it; the note you grab comes on top of the others,
  and stays there. Drag the dotted corner to resize it, from 140 × 100 to 600 × 600. On hover, or while the note is
  selected, the toolbar shows the paperclip, which opens the file menu, the colour dot, which opens five colours
  (paper, yellow, sage, sky and rose), and **×**. A right-click on the note offers the file menu, **Colour** and
  **Delete**; on its text, editing (Undo, Cut, Copy, Paste, Select All).
- A note can carry one file of any kind and size, copied into the note's folder, the original left where it was:
  - drop a file on a note, or use **Attach file…** in its file menu; drop several files on an empty spot to make a
    note of each;
  - copy a file, or an image such as a screenshot, then press Ctrl/⌘ + V on a selected note, or with none selected
    to make a new note; a pasted image is saved as `pasted-image-<time>.png`;
  - a large file copies in the background, its progress on the note.
- An image (PNG, JPEG, GIF, BMP, WebP, TIFF) shows as a picture as wide as the note; a PDF by its first page, beside
  its name and size; anything else by an icon of its kind (document, audio, video, archive…), its name and its size.
  A file deleted from the note's folder outside Psiche Writer shows as missing. Double-click the file, or use
  **Open**, to open it in its program; **Show in folder** opens its folder.
- **Replace file…** and **Remove file** put the file the note had in a copy of the note, among the detached notes.
- Drag an empty spot to move around the board. The wheel scrolls, Shift + wheel sideways, and Ctrl/⌘ + wheel or a
  pinch zooms around the pointer, from the whole board in view up to 200 %; so do the corner's **−**, **+** and
  **Fit**, and Ctrl/⌘ with **+**, **−** and **0** once the board has the focus. Each chapter's board opens again where
  you left it.
- **×**, **Delete** in the menu, or the Delete key on a selected note moves it out of the chapter, to a folder named
  after the chapter and the time (see [Book layout](#book-layout)); nothing is asked, as nothing is lost.
- **Detached notes**, below Deleted chapters, shows while notes are kept for no chapter: notes deleted on their own,
  those of a deleted chapter, files a note no longer carries, and the notes of a chapter the book doesn't have, renamed
  or deleted outside Psiche Writer, or imported from another book. It works without Git. Each group shows its chapter,
  why and when it was left, and its notes as small cards:
  - **Restore to chapter** puts the notes back on the board of the chapter you pick, their own if the book still
    has it, where they were;
  - **Delete permanently** deletes them and their files for good, after asking: notes are not in Git, so nothing
    brings them back.
- Restoring a chapter from Deleted chapters asks **Restore its notes too?**: **Restore notes** puts back the notes it
  had when it was deleted; **Chapter only** leaves them in Detached notes.
- Notes stay out of Git: a book's first note, made or imported, adds `notes/` to its `.gitignore`, in a commit of its
  own. To move them to another computer, or keep a copy, use the Book menu:
  - **Export notes** writes every note, Detached notes included, to a zip you pick, by default next to the book's
    folder as `<title> - notes <date>.zip`. Text is compressed and every other file stored as it is, so even
    gigabytes of video copy at the speed of the disk; the status bar shows how far it has come.
  - **Import notes** merges a zip made by Export notes into the book's notes. It first saves this computer's notes to
    `<title> - notes backup <date and time>.zip` next to the book's folder, shows its progress over the window until
    it is done, then tells what it did. Each note remembers what it was at its last export or import here, so:
    - a note this computer doesn't have is added;
    - a note whose text or file changed only on the other computer takes the new ones; one changed only here stays
      as it is;
    - a note whose text or file changed on both is kept twice: the imported version sits beside the other, with a
      red **Duplicate on Import** band, which its **×** clears once you have kept what you want;
    - a note moved, resized or recoloured only on the other computer takes its new place, size and colour; changed
      on both, it keeps this computer's;
    - deletions are not carried over, and a note stays in the chapter, or among the Detached notes, where it is here;
    - a zip of another book asks first; the notes of chapters this book doesn't have go to Detached notes.

### Git
- **Commit**: records every change in the book (new, modified and deleted files) in one commit, using the message in
  the toolbar field. Press Enter in the field or click the button.
- **Push**: pushes the current branch to the branch of the same name on `origin`. HTTPS remotes authenticate with
  credentials from `~/.netrc`.
- Git commands run in the background, one at a time, in the order you clicked them. Errors (nothing to commit, no
  `origin`, rejected push, …) are shown in a dialog and in the status bar.
- Books without a Git repository can still be edited. The Git controls stay disabled until you run `git init` in the
  folder.

### Chapter history
- The preview sits in a **Preview** tab. Next to it, the **History** tab lists every commit that changed the open
  chapter, newest first: the commit message, the lines it added (green) and removed (red), the short commit id and
  the date. The counts match `git log --numstat`.
- Like `git log --follow`, the history goes on through renames, so renumbering a chapter (`01-` to `03-`) keeps its
  past. The commit that renamed it says *renamed from …* and counts only the lines edited on the way, and older
  versions open under the name the chapter had then.
- The list is read in the background, only while the tab is open. It reloads when you open the tab, switch chapter
  or commit, so commits made outside Psiche Writer show up too.
- **Double-click** a commit (or select it and press Enter) to compare that version with the saved chapter, shown as
  tracked changes like in a word processor. The chapter reads as it is now: text written since that version is
  underlined, and text removed since then is struck through in place. Only the words that changed are marked; a
  paragraph rewritten beyond recognition shows as the old one struck through, then the new one. A summary counts the
  paragraphs changed, added and removed, and the view scrolls to the first change. Unsaved edits are not part of the
  comparison, and the summary says so when there are some.
- **Restore this version** puts that version in the editor in place of the current text. It is not saved: the chapter is marked
  *unsaved* like after any edit, and `Ctrl+Z` / `Cmd+Z` brings your text back. **Close** leaves the chapter as it is.

## Requirements

- **JDK 25.** The Gradle toolchain downloads it automatically if it's missing (via the Foojay resolver).
- **Linux, Windows or macOS** (x64 or aarch64). The JavaFX native libraries for the build machine are selected at
  build time, so a build only runs on the platform it was made on.
- No other installs. Gradle comes through the included wrapper (9.8.2). JavaFX, JGit, commonmark and OpenHTMLtoPDF (with
  Apache PDFBox) are fetched from Maven Central. A system `git` is not required.

## Build and run

```sh
./gradlew run                                  # start with no book open
./gradlew run --args="/path/to/my-book"        # open a book on start
./gradlew test                                 # run the test suite
./gradlew installDist                          # launcher in build/install/psiche/bin/psiche
```

The tests use their own empty home directory under `build/test-home`, so your `~/.gitconfig` and `~/.netrc` don't
affect them. `ChapterWatcherTest` needs a free inotify instance on Linux. If none is left (*"User limit of inotify
instances reached"*), the test is reported as skipped, not failed, so the watcher is then untested.

## Packaging

Psiche Writer ships as a standalone app that brings its own Java runtime, so nothing needs installing: one file to
double-click on Linux, a folder to unzip on Windows. `jlink` makes a runtime of the JDK modules the app needs, and
`jpackage` puts it next to the app's jars, all from the JDK. `jpackage` only packages for the system it runs on, so
each package is made on its own system. Each package has the licence, `LICENSE`, next to the app's launcher.

```sh
./gradlew appImage -Pappimagetool=/path/to/appimagetool   # Linux: build/distributions/psiche.AppImage
./gradlew portableZip                                      # Windows: build/distributions/psiche-windows.zip
```

Or run the script for your system, in the project's root:

- `./build_appimage.sh [path/to/appimagetool]` on Linux. It finds appimagetool as its argument, in the
  `APPIMAGETOOL` variable, or on the `PATH`, and says where to download it when none of these has it.
- `build_exe.bat` on Windows. Gradle needs Java 17 or later to start, and downloads the JDK 25 the app is built with
  if that is missing.

On GitHub, the Package workflow (`.github/workflows/package.yml`) builds both on their own systems: run it from the
Actions tab to get them as the run's artifacts, or push a tag such as `v0.1.0` to also attach them to a draft release
for that tag.

What each package holds:

- **Linux, `psiche.AppImage`:** needs [appimagetool](https://github.com/AppImage/appimagetool) (on the `PATH`, or
  named with `-Pappimagetool`). Make the file executable once, then double-click it. Settings go in
  `~/.config/psiche`; a `psiche.AppImage.config` folder next to the file keeps them there instead.
- **Windows, `psiche-windows.zip`:** unzip it anywhere and double-click `psiche\psiche.exe`. Settings go in
  `%APPDATA%\psiche`; a `settings` folder made next to `psiche.exe` keeps them there instead, so the folder works from
  a USB stick. (On Linux the same task makes `psiche-linux.zip`, whose portable folder is `psiche/bin/settings`.)
- The packages show the app's icon, from the brand kit's dark icon: `src/package/icons` holds `psiche.png` and
  `psiche.ico` (16 to 256 pixels, the small sizes drawn by hand), and the window uses the same sizes.
- Both are about 100 MB: the runtime, JavaFX with its web engine, JGit and PDFBox. Neither is signed, so Windows
  SmartScreen may ask to confirm the first start.

## Architecture

| Package | Responsibility |
|---|---|
| `it.infog.psiche.book` | `BookFolder` (create, open and validate a book; create, list, read and write chapters; read and save the book's properties, set or remove the cover, and add the default ebook stylesheet), `Chapter`, and `ChapterWatcher` (watches `chapters/` for added or removed files). `book.model.BookProperties` holds the properties |
| `it.infog.psiche.git` | `BookRepository`: init, commit (staging every change itself), adding a line to `.gitignore` and committing it alone, push, and the files changed since the last commit, via JGit. `BookHistory`, from `BookRepository.history()`, reads what the commits recorded: a file's history as `FileRevision`s with lines added and removed, whether a name was ever used, the deleted chapters, and a file as a commit recorded it. `TrackedChanges` compares a past version of a chapter with the chapter now, word by word, as tracked changes |
| `it.infog.psiche.settings` | The app's own settings. `SettingsFolders` finds their folder (an explicit one, a portable `settings` folder next to the launcher, or the system's settings folder), `Settings` reads and safely writes `settings.properties` and `words.txt`, the author's own words for the spell checker, shared by every book, `model.RecentBooks` keeps the books opened lately: latest first, each once, at most 10, `model.Preferences` what the author chose in Preferences (with `model.enums.PreviewTheme`, and the font sizes it offers), and `model.WindowBounds` where the window was. A value missing or unreadable reads as its default |
| `it.infog.psiche.markdown` | `MarkdownRenderer`: commonmark → standalone HTML page with the preview stylesheet inlined, each block marked with where it starts and ends in the Markdown, in characters (`data-start`, `data-end`). `HtmlComments`: where a chapter's comments are, found in its HTML by the parser (`model.TextRange`), for the editor to grey, and HTML without them, for the PDF and ODT exports |
| `it.infog.psiche.notes` | `NoteStore`: the chapters' notes in `notes/`, one folder per chapter and one per note (`note.md` for the text, `note.properties` for its place, size, colour and tag and the name of its file, and the file itself, copied in with its progress reported), written through a temporary file, a note's properties changed one thread at a time. A renamed chapter's notes move with it; a deleted chapter's, or a deleted note, go to `DetachedNoteStore`, from `NoteStore.detached()`, and so does a file replaced or removed, with a copy of its note. `DetachedNoteStore` keeps them in a `-del-` folder with the time and why, and lists the folders that match no chapter as orphans, for Detached notes to restore or delete for good. How a note sits on disk, read and written whole, and how notes move between folders, is `NoteFolders`'. `NotesArchive` exports the notes to a zip, with a manifest naming the book, text compressed and other files stored, Zip64 past 4 GB, each note remembering the fingerprints `NotePrints` takes of its content and of its layout; `ArchiveImport` merges a zip back by note id, after checking that no path leaves `notes/`. `notes.model` holds `Note`, `NoteLayout`, `BoardView`, `OrphanGroup`, `NotesManifest` and `ImportReport`; `notes.model.enums.FileKind` tells a file's kind by its extension |
| `it.infog.psiche.spelling` | Spell checking, offline. `SpellChecker` checks words in one language with the Hunspell dictionary the app brings for it, through Lucene's Hunspell, and suggests others; it takes the author's own words, a curly apostrophe as a straight one, and a closing quote after a word. `ProseWords` finds the words of a chapter's prose, leaving out code, HTML and comments, link and image targets, addresses, entities and words with digits. `Dictionaries` reads the list of the dictionaries the app brings, `dictionaries.txt` in the resources (English, Italian, French, Spanish, German, and Portuguese from Brazil and from Portugal), and picks a book's: its country's, or else the first of its language; each is a `model.SpellingLanguage`. A dictionary, from LibreOffice, is a folder named by its tag with its licence, and adding or removing one takes its folder and its line, no code; `SOURCES.md` says where each came from and how |
| `it.infog.psiche.export` | `ChapterExporter`: a chapter as Markdown, PDF or ODT. `BookAssembler` puts the whole book together (`export.model.BookExport`): each chapter parsed on its own, with an id (`chapter-1`, …) and a title (its first heading, or its file name), and empty chapters skipped or kept as placeholders, a `TitlePage` and a `Contents` that decide once what every format's title page and contents show; `BookExporter` writes it. `XhtmlRenderer` renders Markdown, the title page and the contents as XHTML: for a manuscript (the PDF) or for an ebook (the EPUB, with real scene-break rules and images found through `ImageLinks`). `ZipContainer` is the zip with its mimetype first that ODT and EPUB share, and `Template` the XML templates with `{{placeholders}}` they fill |
| `it.infog.psiche.export.pdf` | `ManuscriptPdf` lays out XHTML as a manuscript PDF through OpenHTMLtoPDF with Libertinus embedded: `manuscript.css` for a chapter, plus `book.css` for the book's title page and page breaks. `BundledFontMapper` keeps PDFBox from scanning the computer's fonts |
| `it.infog.psiche.export.epub` | The EPUB export. `EpubWriter` checks the book for what stores may object to, and writes it: `Pages` makes each XHTML page from `page.xhtml` (cover, title page, chapters, copyright), `Navigation` the contents page with its landmarks and `toc.ncx`, `PackageDocument` the `package.opf` with the metadata from `Accessibility`; `EpubImages` packs the cover and the chapters' images, shrinking the large ones |
| `it.infog.psiche.export.odt` | The ODT export, of a chapter or the whole book. `OdtWriter` fills the document's XML parts from templates (`content.xml`, `styles.xml`, `meta.xml`) and has `OdtPackage` zip them with the images `Pictures` found. `ContentRenderer` (and for the book `TitlePageRenderer` and `ContentsRenderer`) writes the text into an `OdtBody` as paragraphs and spans that only name a `Style`, whose look `styles.xml` defines; `OdtBody` gives the first paragraph after a `PageStart` (title page, first chapter, later chapter) the automatic style that starts the page. `OdtText` escapes text and keeps its spaces |
| `it.infog.psiche.ui` | The JavaFX window: `WriterApp` and `BookWorkspace`, with the components below in a sub-package per theme: `ui.book`, `ui.chapters`, `ui.preview`, `ui.git`, `ui.export` and `ui.preferences`, and `ui.common` for what they all use |

Only the `ui` package depends on the others. `book`, `git`, `markdown`, `export` and `settings` don't depend on JavaFX,
and they have unit tests; `export` reads the book's properties from `book.model`.

### UI components

```
WriterApp ── BookWorkspace ─┬─ toolbar:   Book menu (New book · Open book · Save chapters · Book properties · Export · Export notes · Import notes) · Preferences · GitControls
                            ├─ sidebar:   ChapterContext (chapter list · New chapter · Deleted chapters · Detached notes)
                            ├─ pages:     ChapterEditor (Manuscript · MarginMarks · Mistakes · FormattingMenu) │ tabs: PreviewPane · NotesPane · HistoryPane ── RevisionDialog
                            └─ StatusBar
```

| Class | Responsibility |
|---|---|
| `WriterApp` | Entry point: loads the fonts and stylesheet, binds `Ctrl+S` / `Cmd+S` (save the open chapter) and `Ctrl+Shift+S` / `Cmd+Shift+S` (save all), asks about unsaved chapters before the window closes, finds the settings, puts the window where it was last time and remembers it on closing, makes the preview's renderer for each theme, opens the book given on the command line, or else the last book if Preferences asks for it |
| `BookWorkspace` | Lays out the window, creates the shared parts and wires them together; reports errors in the status bar and an alert |
| `book.BookContext` | The open book: opens, creates and closes books (folder, Git repository, chapter watcher), edits the book's properties and cover and how its pages are counted, and hands the book to `ChapterContext`, and remembers each book opened, for **Open recent** |
| `book.RecentBooksMenu` | **Open recent** in the Book menu, filled from the settings each time the menu opens: the books by title, a missing one greyed out, and Clear list |
| `book.BookPropertiesDialog` | The *Book properties* form: title, subtitle, authors, language and copyright, plus the cover's thumbnail with *Choose image…* and *Remove*. It returns the edit, and the workspace writes it |
| `chapters.ChapterContext` | The open book's chapters: the chapter list, drawn by `ChapterCell`, and its right-click menu, opening a chapter in the editor, restoring a past version into it, and keeping the preview, notes, history, counts and status bar in step with the editor; creating, renaming and deleting a chapter are left to `ChapterChanges`, exporting one to `Exports`, restoring a deleted one to `DeletedChapters`, and the notes left without a chapter to `DetachedNotes`, whose button it shows only while there are any |
| `chapters.ChapterChanges` | Creates, renames and deletes chapters after asking the author; a renamed or deleted chapter's notes move with it, through `NoteStore`, and the rename or deletion is committed alone, once everything else is saved and committed |
| `chapters.ChapterCell` | A chapter in the list: its revision dot, its title, the *unsaved* mark while it has unsaved changes, and its menu |
| `chapters.ChapterRevisions` | Which chapters changed since the last commit, for the revision dots: read from Git on the Git thread when the list is refreshed, a chapter is saved or gains unsaved changes, after a commit, and when the window gets the focus back, so commits made outside Psiche Writer show too |
| `chapters.DeletedChapters` | *Deleted chapters*: lists the chapters deleted from the book in `DeletedChaptersDialog`, writes the one picked back with its last text, offers back the notes it had then, and commits it on its own, once everything else is saved and committed |
| `chapters.ChapterEditor` | The manuscript editor for one chapter at a time: which chapter is open, the text last saved, which chapters have unsaved changes, and `open` / `close` / `save` / `saveAll` / `discardAll`, plus `replaceAll`, which swaps in a restored version as one undoable edit. The page it writes on is `Manuscript`, with `FormattingMenu` |
| `chapters.Manuscript` | The page the author writes on, a RichTextFX `StyleClassedTextArea` in a scroll pane: the welcome and its logo until a book opens, the font size from Preferences, where the text starts for the status bar, and the current position for the preview to follow (the caret's while writing, the one a third of the way down the view while scrolling to read). Cut, copy and paste are plain text. The only class that knows which text control the editor uses |
| `chapters.CaretBlink` | Blinks the manuscript's caret from a timer of its own, as RichTextFX's animation would draw the window sixty times a second; only while the editor has the focus in the active window, shown at once on a key or a click, and steady ten seconds after the last one, as in GTK |
| `chapters.MarginMarks` | The dots in the manuscript's margin, one layer per kind: the comments' brass ones in the margin's middle, the spelling mistakes' red ones beside them. Placed after each layout pass so the dots sit on the lines as they show, with a dot per line in view that has such a part, and the edge dots that count those above and below the view and scroll to the next one |
| `chapters.SpellingMenu` | Over a marked word, the spelling part of the manuscript's right-click menu: up to five suggestions, each replacing the word as one undoable change, then Add to dictionary and Ignore, which put it in the author's words for every book |
| `chapters.Mistakes`, `TextStyles`, `Spelling` | The spelling mistakes: found by the book's `SpellChecker` a moment after typing stops, moved with the text meanwhile, and let go when typing touches one; `TextStyles` gives the text its comment and mistake styles together; `Spelling` picks the dictionary of the open book's language while checking is on, reading it on a thread of its own the first time and keeping it |
| `chapters.FormattingMenu` | The manuscript's right-click menu: the Markdown formatting items with their shortcuts, each one change a single Undo reverts, above the editing commands |
| `chapters.Drafts` | Unsaved text per chapter, kept in memory until it is saved or discarded; `saveAll` writes every chapter it can and keeps the drafts of those it cannot |
| `chapters.UnsavedChanges` | Asks whether to save or discard unsaved chapters before a book is replaced, a commit, an export or closing the window |
| `preview.PreviewPane` | The typeset preview in a `WebView`: `render` shows a chapter at once from the top, `renderLater` follows the author's typing after a 500 ms pause and keeps the scroll position, and `followPosition` scrolls to the part of the page rendered from a character of the Markdown, as far down its block as it is through the block's text; while hidden behind the History tab it holds back both and catches up when shown; a render of the page already shown, for the same folder, theme and font size, only replaces its article, without loading the page and its stylesheet again |
| `git.GitControls` | Commit message, Commit and Push. Runs Git commands one at a time on a background thread, exposes a *busy* flag that the book buttons disable on, and reports each commit so the history and the revision dots reload; also adds a line to `.gitignore`, for notes, and reads the book's state quietly, for the revision dots |
| `notes.NotesPane` | The Notes tab: the open chapter's `NoteBoard`, shown in a `BoardViewport` with a `BoardBar` in its corner, and the board's keys; reads a chapter's notes as it opens, again after notes are restored to it, and keeps each chapter's view; asks for pictures only while the tab is showing. `settle()` saves what is pending before a chapter is renamed or deleted, or the app closes |
| `notes.NoteBoard` | The board itself: the notes as cards, the one selected and the order they lie in; makes new notes, by a double-click, New note, or for files dropped or pasted, writes each change a card reports, and moves a deleted note to Detached notes and says so |
| `notes.CardFiles` | The files on the cards: gives a note a file dropped, chosen or pasted, growing the card to show it, takes it away to Detached notes, opens it or its folder, and asks `NoteFiles` for its picture once |
| `notes.NotesThread` | The thread every read and write of the board's notes runs on, in order; it knows which board is shown, so a change goes to the board it was made on, and what is read is handed back only to the same board |
| `notes.BoardViewport` | The part of the board in view: panned by dragging an empty spot or with the wheel, zoomed with Ctrl/⌘ + wheel (caught before the notes see it), a pinch or by steps, from the whole board up to 200 %; keeps the board in view, opens a saved view once it has a size, and hands on the view a second after it settles |
| `notes.BoardBar` | The corner bar with New note and the zoom |
| `notes.DetachedNotes`, `DetachedNotesDialog` | *Detached notes*: the notes left without a chapter, grouped by chapter and cause, put back on a chapter's board or deleted for good after asking, and the *Restore its notes too?* question for a restored chapter; read and moved on the board's notes thread, after what the board had to write |
| `notes.NotesTransfer`, `ImportProgressDialog` | *Export notes* and *Import notes*: the zip picked, by default next to the book's folder, on a thread of their own; the export's progress in the status bar; the import's question for a zip of another book, its backup zip, its progress in a dialog over the window that nothing but the import closes, and what it did, told at the end |
| `notes.NoteFiles` | The files notes carry: copying them in on a thread of their own, with the progress on the card; a pasted image saved as a PNG; the pictures of images, read in the background, and of PDFs, the first page rendered with PDFBox on another thread; opening a file or its folder |
| `notes.card.NoteCard` | One note on the board: the toolbar and Duplicate on Import band it is dragged by, the paperclip's file menu, the colour swatches, the text typed in place and saved a second after typing stops, its file, the resize corner, and its right-click menus, shown in the board's one menu; a single file dropped on it goes into it; it reports each change to the board |
| `notes.card.CardDrag`, `ColorSwatches` | Moving and resizing a note with the pointer, at any zoom, kept on the board; the row of colour swatches, the note's own ringed |
| `notes.card.CardFileSlot`, `AttachmentView`, `FileIcons`, `FileLabels` | A note's file on its card, in a slot under the text that shows the file or the copy in progress and cuts it at the card's edge: picture, cover, or icon with name and size, missing, or copying; the line icons of each kind of file; a size as people read it and a long name shortened in the middle |
| `git.HistoryPane` | The History tab: the open chapter's commits with lines added and removed, read on a background thread while the tab is showing; a double-click or Enter reads that version and hands it on to be shown |
| `git.RevisionDialog` | A past version compared with the saved chapter as tracked changes (written since: underlined; removed since: struck through), with a summary in words, *Close* and *Restore this version* |
| `export.Exports` | Exports a chapter or the whole book: settles unsaved changes, asks about empty chapters, picks the file and writes it on an export thread of its own, reporting in the status bar |
| `preferences.PreferencesDialog` | The *Preferences* form, in two fieldsets: General (reopen the last book at startup, the editor's font size, the preview's theme and font size) and New book defaults (language and author). It hands the workspace what the author applies or saves, and the opening values again on Revert, closing only for Save; the workspace remembers it and changes the editor's and the preview's look |
| `common.StatusBar` | The open chapter's characters, words and pages on the left, lined up with the editor's text (right-click the pages to choose how they are counted), and the latest message (`info` / `error`) and the chapter's saved/unsaved state on the right |
| `common.Dialogs` | Title prompt, choice between buttons, error alert and folder chooser, owned by and styled like the main window |
| `preferences.AboutDialog` | *About Psiche Writer*, opened from Preferences: logo, version (read from the module, where the build writes it), author, licence, the Buy Me a Coffee button, and the licences of the fonts and libraries, shown in the app or opened in the browser |
| `common.LanguageChoice` | The language field of *Book properties* and *Preferences*: shows the language chosen, lists them all on a click, and narrows the list as the author types, names starting with the search first |
| `common.Animations`, `Controls`, `ErrorReporter`, `Logo`, `Icons` | The reveal animation; button, menu item, editing menu, spacer and fieldset factories; the callback components use to report failures; the Psiche logo drawn from the brand kit's SVG files; and the app's small drawings, read from the SVG files in `icons/`, where they can be changed in any SVG editor |

Apart from posting messages to the shared `StatusBar`, the components don't know about each other.
`BookWorkspace` wires them together; `BookContext` passes the open book to `ChapterContext`, and they report back through
callbacks (`ErrorReporter`, the check before committing, the after-commit hook, `HistoryPane.RevisionOpener`) and observables (`dirtyProperty`,
`unsavedChapters`, `busyProperty`).

## Limitations

- No way to reorder chapters from the editor other than renaming their number prefixes.
- Rename, delete and restore need a Git repository, every chapter saved, and every change to the files Git tracks
  committed; the chapter renamed or deleted must be committed too.
- No pull or fetch, and no way to configure remotes. Add `origin` with `git remote add origin <url>`.
- Only HTTPS (via `~/.netrc`) and local remotes are supported for push. SSH remotes are not.
- Chapter files must not be changed by another program while the book is open (see the warning under
  [Book layout](#book-layout)). The editor doesn't reload a chapter changed on disk, and the next save overwrites the
  change. A chapter deleted on disk with unsaved changes is written back by **Save chapters**.
- Switching chapter clears the editor's undo history, so a restore can be undone only until you switch chapter.
- Git keeps history by file name, so a new chapter that takes the name of an earlier one, deleted or renamed since,
  shows the earlier chapter's versions in its History too (for example: delete `01-lorem`, then create a new
  `01-lorem`). **New chapter** warns before this happens. Renaming a chapter onto such a name is not affected:
  its History follows the renamed chapter only.
- Renames are recognised the way Git does it: the old and new file must be at least 60% similar, so a chapter
  renamed and largely rewritten in the same commit starts a new history.
- Changed words are found within a paragraph and its most similar counterpart (at least half the words in common).
  A paragraph split in two, or two joined into one, shows as one edited paragraph plus one written or removed.
- Only committed versions are listed; saved but uncommitted changes have no entry in the history.
- Every import that changes something leaves its backup zip next to the book's folder, as large as the notes were;
  remove the old ones yourself.
- A file still copying into a note when the app closes is not attached; only a hidden temporary file is left in the
  note's folder.
- A chapter exports to a manuscript PDF or ODT, or to Markdown; the whole book to a manuscript PDF or ODT, or to
  EPUB, each with the same contents after the title page. Links between chapters, and DOCX, are not there yet.
- The EPUB declares no accessibility conformance (`dcterms:conformsTo`): that needs someone to evaluate the book.

## Licence

Psiche Writer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
version. It is distributed in the hope that it will be useful, but without any warranty; without even the implied
warranty of merchantability or fitness for a particular purpose. The full text of version 3 is in [LICENSE](LICENSE),
and inside the app.

The fonts and libraries it ships with keep their own licences:

| Part | Licence |
|---|---|
| Libertinus Serif, Courier Prime (fonts) | SIL Open Font License 1.1, its text next to each font in `ui/fonts` |
| OpenHTMLtoPDF | GNU Lesser General Public License 2.1 or later |
| Apache PDFBox, FontBox, XmpBox, pdfbox-graphics2d, Apache Commons Codec and Logging, JavaEWAH, Apache Lucene (spell checking) | Apache License 2.0 |
| JGit | Eclipse Distribution License 1.0 (BSD 3-Clause) |
| commonmark-java | BSD 2-Clause |
| SLF4J | MIT License |
| TwelveMonkeys ImageIO (WebP pictures) | BSD 3-Clause |
| RichTextFX, and the Flowless, ReactFX, UndoFX and WellBehavedFX libraries it uses (the editor's text) | BSD 2-Clause |
| OpenJFX, and the Java runtime in the packages | GNU General Public License 2 with the Classpath Exception |
| The spelling dictionaries, from LibreOffice: English (SCOWL), Italian (LibreItalia), French (Grammalecte), Spanish (RLA-ES), German (igerman98, frami), Portuguese from Brazil (VERO) and from Portugal (Universidade do Minho) | Each its own: GPL, LGPL, MPL, BSD or SCOWL's permissive notice, as `spelling/dictionaries.txt` and `spelling/SOURCES.md` say, its README beside it |

The Buy Me a Coffee logo on the About dialog's support button is Buy Me a Coffee's trademark, from their brand bundle,
and links only to their page.
