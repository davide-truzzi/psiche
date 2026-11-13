package it.infog.psiche.export.pdf;

import it.infog.psiche.export.TitleMark;
import it.infog.psiche.export.pdf.model.Drawing;
import it.infog.psiche.export.pdf.model.DrawingPart;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SvgDrawingTest {

    @Test
    void theMarkIsThreeLinesAndARingStrokedThenThreeDotsFilled() {
        Drawing mark = SvgDrawing.read(TitleMark.svg());

        assertThat(mark.viewBox()).isEqualTo(new Rectangle2D.Double(22, 8, 76, 108));
        assertThat(mark.parts()).hasSize(7);
        assertThat(mark.parts().subList(0, 4)).allSatisfy(part -> {
            assertThat(part.stroked()).isTrue();
            assertThat(part.filled()).isFalse();
            assertThat(part.strokeWidth()).isEqualTo(4);
        });
        assertThat(mark.parts().subList(4, 7)).allSatisfy(part -> {
            assertThat(part.filled()).isTrue();
            assertThat(part.stroked()).isFalse();
        });
        assertThat(mark.parts().getFirst().shape().getBounds2D()).isEqualTo(new Rectangle2D.Double(60, 23, 0, 76));
    }

    @Test
    void aPathReadsItsMovesLinesAndCurves() {
        Rectangle2D bounds = SvgDrawing.path("M10 10H30V20L10 20Z M0 0C0 5 5 5 5 0").getBounds2D();

        assertThat(bounds).isEqualTo(new Rectangle2D.Double(0, 0, 30, 20));
    }

    @Test
    void aPathCommandItCannotReadIsReported() {
        assertThatThrownBy(() -> SvgDrawing.path("M0 0 a 5 5 0 0 0 10 0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'a'");
    }

    @Test
    void aShapeFilledByDefaultHasNoOutline() {
        DrawingPart dot = SvgDrawing.read("<svg viewBox=\"0 0 10 10\"><circle cx=\"5\" cy=\"5\" r=\"2\"/></svg>")
                .parts().getFirst();

        assertThat(dot.filled()).isTrue();
        assertThat(dot.stroked()).isFalse();
    }
}
