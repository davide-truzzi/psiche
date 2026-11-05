package it.infog.psiche.ui.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppInfoTest {

    @Test
    void aVersionShowsItsStageAfterASpaceWithACapital() {

        assertThat(AppInfo.readable("0.3.0-alpha")).isEqualTo("0.3.0 Alpha");
        assertThat(AppInfo.readable("1.0.0-rc1")).isEqualTo("1.0.0 Rc1");
    }

    @Test
    void aStableVersionShowsAsItIs() {

        assertThat(AppInfo.readable("1.0.0")).isEqualTo("1.0.0");
        assertThat(AppInfo.readable("1.0.0-")).isEqualTo("1.0.0-");
    }
}
