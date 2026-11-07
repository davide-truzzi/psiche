package it.infog.psiche.git.model.enums;

/**
 * What a folder chosen as a book's remote holds, and so whether it can be one: a bare repository, used as it is, or
 * any other folder, where the book's repository is kept in Psiche's own folder, {@code PsicheRepository}; a working
 * copy, the book's own folder, or a {@code PsicheRepository} holding other files are refused, saying why.
 */
public enum FolderState {

    // A bare repository, made by Psiche on another device or by hand.
    REPOSITORY(true, ""),
    // A folder whose PsicheRepository holds the book's repository already, made by Psiche on another device.
    BOOK_REPOSITORY(true, ""),
    // A folder without the book's repository: it is made on Save, in PsicheRepository, made too if needed.
    NEW_REPOSITORY(true, ""),
    MISSING(false, "The folder is not there: is the drive plugged in?"),
    NOT_WRITABLE(false, "Psiche cannot write in this folder."),
    INSIDE_BOOK(false, "The folder is the book's own, or inside it: choose one on another drive."),
    WORKING_REPOSITORY(false, "The folder holds a Git working copy, not a bare repository: choose another folder."),
    HAS_FILES(false, "Its PsicheRepository folder holds files Psiche did not make: move them, or choose another "
            + "folder.");

    private final boolean usable;
    private final String problem;

    FolderState(boolean usable, String problem) {

        this.usable = usable;
        this.problem = problem;
    }

    /** True if the folder can be the book's remote. */
    public boolean usable() {

        return usable;
    }

    /** Why the folder cannot be the book's remote; blank if it can. */
    public String problem() {

        return problem;
    }
}
