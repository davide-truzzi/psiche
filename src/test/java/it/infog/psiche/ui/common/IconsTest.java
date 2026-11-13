package it.infog.psiche.ui.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IconsTest {

    @Test
    void everyIconInTheFolderHasADrawing() throws IOException, URISyntaxException {
        Path folder = Path.of(Icons.class.getResource("/it/infog/psiche/ui/icons/grip.svg").toURI()).getParent();
        List<String> names;
        try (Stream<Path> files = Files.list(folder)) {
            names = files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".svg"))
                    .map(name -> name.substring(0, name.length() - ".svg".length())).toList();
        }

        assertThat(names).contains("file-image", "file-missing", "paperclip");
        // A path starts by moving to its first point: "M", or "m", which SVG reads the same there and Inkscape writes.
        assertThat(names).allSatisfy(name -> assertThat(Icons.pathData(name)).matches("(?s)[Mm].*"));
    }

    @Test
    void theStrokesOfAnIconAreJoinedInOrder() {
        assertThat(Icons.pathData("file-other")).isEqualTo("M8 3h10l6 6v18H8z M18 3v6h6");
    }

    @Test
    void anIconNotInTheFolderIsReported() {
        assertThatThrownBy(() -> Icons.pathData("no-such-icon"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no-such-icon.svg");
    }
}
