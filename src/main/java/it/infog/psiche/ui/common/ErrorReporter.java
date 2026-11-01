package it.infog.psiche.ui.common;

/** Tells the author that an action failed, with {@code header} as the one-line summary. */
@FunctionalInterface
public interface ErrorReporter {

    void report(String header, Throwable error);
}
