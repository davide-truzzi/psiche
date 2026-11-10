package it.infog.psiche.markdown.model;

/** Text set below the line between two tildes, as the 2 of {@code H~2~O}. */
public final class Subscript extends Script {

    public static final char SIGN = '~';

    public Subscript() {

        super(SIGN);
    }
}
