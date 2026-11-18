package it.infog.psiche.update;

import it.infog.psiche.update.exceptions.UpdateCheckException;
import it.infog.psiche.update.model.Release;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LatestReleaseTest {

    private static final String ANSWER = """
            {"version":"0.9.1 Beta","tag":"v0.9.1-beta","date":"2026-11-18",\
            "notesUrl":"https://github.com/davide-truzzi/psiche/releases/tag/v0.9.1-beta",\
            "downloadUrl":"https://psiche.app/download",\
            "assets":{"linux":{"name":"psiche.AppImage","url":"https://github.com/x/psiche.AppImage","size":1},\
            "windows":{"name":"psiche-windows.zip","url":"https://github.com/x/psiche-windows.zip","size":null}}}""";

    @Test
    void theSitesAnswerGivesTheReleasesNameTagAndDownloadPage() {

        assertThat(LatestRelease.parse(ANSWER))
                .isEqualTo(new Release("0.9.1 Beta", "v0.9.1-beta", "https://psiche.app/download"));
    }

    @Test
    void escapedSlashesAndSpacesAroundColonsAreRead() {

        String answer = """
                { "version" : "1.0.0", "tag" : "v1.0.0", "downloadUrl" : "https:\\/\\/psiche.app\\/download" }""";

        assertThat(LatestRelease.parse(answer).downloadUrl()).isEqualTo("https://psiche.app/download");
    }

    @Test
    void anAnswerWithoutATagCannotBeRead() {

        assertThatThrownBy(() -> LatestRelease.parse("{\"version\":\"1.0.0\",\"downloadUrl\":\"x\"}"))
                .isInstanceOf(UpdateCheckException.class);
        assertThatThrownBy(() -> LatestRelease.parse("{\"error\":\"method_not_allowed\"}"))
                .isInstanceOf(UpdateCheckException.class);
    }

    @Test
    void aTagThatNamesNoVersionCannotBeRead() {

        assertThatThrownBy(() -> LatestRelease.parse("{\"version\":\"\",\"tag\":\"\",\"downloadUrl\":\"x\"}"))
                .isInstanceOf(UpdateCheckException.class);
    }

    @Test
    void aLaterNumberOrStageIsNewer() {

        assertThat(LatestRelease.isNewer(release("v0.9.2-beta"), "0.9.1-beta")).isTrue();
        assertThat(LatestRelease.isNewer(release("v0.9.10-beta"), "0.9.9-beta")).isTrue();
        assertThat(LatestRelease.isNewer(release("v0.9.1-beta"), "0.9.1-alpha")).isTrue();
        assertThat(LatestRelease.isNewer(release("v1.0.0-rc1"), "0.9.1-beta")).isTrue();
        assertThat(LatestRelease.isNewer(release("v1.0.0"), "1.0.0-rc1")).isTrue();
    }

    @Test
    void theSameOrAnEarlierReleaseIsNotNewer() {

        assertThat(LatestRelease.isNewer(release("v0.9.1-beta"), "0.9.1-beta")).isFalse();
        assertThat(LatestRelease.isNewer(release("v0.9.0-beta"), "0.9.1-beta")).isFalse();
        assertThat(LatestRelease.isNewer(release("v0.9.1-beta"), "0.9.1")).isFalse();
    }

    private static Release release(String tag) {

        return new Release(tag, tag, "https://psiche.app/download");
    }
}
