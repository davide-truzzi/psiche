package it.infog.psiche.book;

import java.nio.file.Path;

public record Chapter(Path file) {

    public String title() {

        String name = file.getFileName().toString();
        return name.substring(0, name.length() - BookFolder.CHAPTER_EXTENSION.length());
    }
}
