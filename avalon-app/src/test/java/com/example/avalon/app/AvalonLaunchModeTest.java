package com.example.avalon.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AvalonLaunchModeTest {
    @Test
    void shouldDefaultToConsoleMode() {
        AvalonLaunchMode mode = AvalonLaunchMode.resolve(new String[0]);

        assertThat(mode).isEqualTo(AvalonLaunchMode.CONSOLE);
        assertThat(mode.launchArgs(new String[0]))
                .contains("--avalon.console.enabled=true")
                .contains("--spring.main.web-application-type=none");
    }

    @Test
    void shouldSupportExplicitServerMode() {
        AvalonLaunchMode mode = AvalonLaunchMode.resolve(new String[] {"--avalon.mode=server"});

        assertThat(mode).isEqualTo(AvalonLaunchMode.SERVER);
        assertThat(mode.launchArgs(new String[0]))
                .contains("--avalon.console.enabled=false")
                .contains("--spring.main.web-application-type=servlet");
    }

    @Test
    void shouldSupportExplicitConsoleMode() {
        AvalonLaunchMode mode = AvalonLaunchMode.resolve(new String[] {"--avalon.mode=console"});

        assertThat(mode).isEqualTo(AvalonLaunchMode.CONSOLE);
    }

    @Test
    void shouldOverrideConflictingArgsForSelectedMode() {
        String[] launchArgs = AvalonLaunchMode.CONSOLE.launchArgs(new String[] {
                "--avalon.console.enabled=false",
                "--spring.main.web-application-type=servlet"
        });

        assertThat(launchArgs)
                .contains("--avalon.console.enabled=true")
                .contains("--spring.main.web-application-type=none")
                .doesNotContain("--avalon.console.enabled=false")
                .doesNotContain("--spring.main.web-application-type=servlet");
    }
}
