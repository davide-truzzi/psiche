package it.infog.psiche.export;

import java.util.Optional;

/** Where an exported page finds an image a chapter shows, such as the copy an EPUB packs. */
@FunctionalInterface
public interface ImageLinks {

    /**
     * @param destination the image's link as the chapter writes it, such as {@code ../media/map.png}
     * @return the image's address in the exported file; empty to leave the image out
     */
    Optional<String> href(String destination);
}
