package it.infog.psiche.media.model;

import it.infog.psiche.media.model.enums.DetachCause;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Images in a folder of {@code media/} that belongs to no chapter of the book.
 *
 * @param folder    the folder that holds them
 * @param chapter   the chapter they belonged to, by name
 * @param cause     how they were left without a chapter
 * @param deletedAt when Psiche detached them or deleted their chapter; empty for {@link DetachCause#CHAPTER_GONE}
 * @param images    the images, by name
 */
public record DetachedGroup(Path folder,
                             String chapter,
                             DetachCause cause,
                             Optional<LocalDateTime> deletedAt,
                             List<MediaImage> images) {

    public DetachedGroup {

        images = List.copyOf(images);
    }
}
