package it.infog.psiche.notes.model.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** The colours a note comes in, as post-its do; the board draws each one. */
public enum NoteColor {

    PAPER("Paper"),
    YELLOW("Yellow"),
    SAGE("Sage"),
    SKY("Sky"),
    ROSE("Rose");

    private final String label;

    NoteColor(String label) {

        this.label = label;
    }

    /** The colour saved as {@code key}, if it names one. */
    public static Optional<NoteColor> fromKey(String key) {

        return Arrays.stream(values()).filter(color -> color.key().equals(key)).findFirst();
    }

    /** The name the author picks the colour by. */
    public String label() {

        return label;
    }

    /** The name the colour is saved under in a note's properties. */
    public String key() {

        return name().toLowerCase(Locale.ROOT);
    }
}
