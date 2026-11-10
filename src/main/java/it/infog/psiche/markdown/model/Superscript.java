package it.infog.psiche.markdown.model;

/** Text set above the line between two carets, as the 2 of {@code X^2^}. */
public final class Superscript extends Script {

    public static final char SIGN = '^';

    public Superscript() {

        super(SIGN);
    }
}
