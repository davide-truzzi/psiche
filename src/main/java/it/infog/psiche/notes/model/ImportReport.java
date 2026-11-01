package it.infog.psiche.notes.model;

/**
 * What an import of notes did.
 *
 * @param added      notes this computer did not have
 * @param updated    notes changed only in the export, whose text, file, place or colour were taken from it
 * @param duplicated notes changed on both computers, kept twice: the imported copy tagged Duplicate on Import
 */
public record ImportReport(int added,
                           int updated,
                           int duplicated) {

    /** True if the import changed nothing: this computer had every note as the export has it. */
    public boolean isEmpty() {

        return added == 0 && updated == 0 && duplicated == 0;
    }
}
