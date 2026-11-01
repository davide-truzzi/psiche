package it.infog.psiche.export;

import it.infog.psiche.export.odt.OdtWriter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateTest {

    @Test
    void placeholdersInAValueAreLeftAsTheAuthorWroteThem() {
        String meta = Template.load(OdtWriter.class, "meta.xml").fill(Map.of("title", "About {{authors}}",
                "authors", "Anna Bianchi", "language", "it-IT"));

        assertThat(meta).contains("<dc:title>About {{authors}}</dc:title>", "<dc:creator>Anna Bianchi</dc:creator>");
    }
}
