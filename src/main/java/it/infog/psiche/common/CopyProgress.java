package it.infog.psiche.common;

/** Hears how far a copy has come, on the thread that copies. */
@FunctionalInterface
public interface CopyProgress {

    /** {@code bytes} of {@code total} are copied so far. */
    void copied(long bytes, long total);
}
