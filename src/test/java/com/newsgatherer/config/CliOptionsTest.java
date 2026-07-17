package com.newsgatherer.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CliOptionsTest {

    @Test
    void parsesBackfillWindowAndOutputOptions() {
        CliOptions options = CliOptions.parse(new String[] {
            "--query=domain:example.com",
            "--timespan=45m",
            "--lookback=7d",
            "--window=30m",
            "--json-output=output/articles.ndjson"
        });

        assertThat(options.query()).isEqualTo("domain:example.com");
        assertThat(options.timespan()).isEqualTo("45m");
        assertThat(options.lookback()).contains(Duration.ofDays(7));
        assertThat(options.window()).isEqualTo(Duration.ofMinutes(30));
        assertThat(options.jsonOutput()).contains(Path.of("output/articles.ndjson"));
    }

    @Test
    void ignoresBlankArgumentsAndKeepsSafeDefaults() {
        CliOptions options = CliOptions.parse(new String[] {null, "", "   "});

        assertThat(options.lookback()).isEmpty();
        assertThat(options.window()).isEqualTo(Duration.ofHours(1));
        assertThat(options.jsonOutput()).isEmpty();
    }

    @Test
    void rejectsUnknownOrInvalidOptions() {
        assertThatThrownBy(() -> CliOptions.parse(new String[] {"--unknown=value"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unknown argument: --unknown=value");
        assertThatThrownBy(() -> CliOptions.parse(new String[] {"--lookback=0d"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Duration must be positive: 0d");
        assertThatThrownBy(() -> CliOptions.parse(new String[] {"--window=ten"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid duration 'ten'");
        assertThatThrownBy(() -> CliOptions.parse(new String[] {"--json-output=  "}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("--json-output requires a file path");
    }
}
