package it.infog.psiche.ui.notes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileLabelsTest {

    @Test
    void sizesReadAsPeopleWriteThem() {
        assertThat(FileLabels.readableSize(512)).isEqualTo("512 B");
        assertThat(FileLabels.readableSize(240 * 1024)).isEqualTo("240 KB");
        assertThat(FileLabels.readableSize(19_293_798)).isEqualTo("18.4 MB");
        assertThat(FileLabels.readableSize(1_288_490_189L)).isEqualTo("1.2 GB");
    }

    @Test
    void aLongNameIsShortenedInTheMiddle() {
        assertThat(FileLabels.shortened("castello.jpg")).isEqualTo("castello.jpg");
        assertThat(FileLabels.shortened("le-memorie-di-una-vecchia-ventura.pdf"))
                .hasSize(28).startsWith("le-memorie-di-").endsWith("…a-ventura.pdf");
    }
}
