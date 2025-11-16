package com.newsgatherer.config;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight CLI argument parser for configuring the gatherer at runtime.
 */
public record CliOptions(
    String query,
    String timespan,
    Optional<Duration> lookback,
    Duration window
) {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(?i)^(\\d+)([smhd])$");
    private static final Duration DEFAULT_WINDOW = Duration.ofHours(1);

    public static CliOptions parse(String[] args) {
        String query = Config.DEFAULT_QUERY;
        String timespan = Config.DEFAULT_TIMESPAN;
        Duration lookback = null;
        Duration window = DEFAULT_WINDOW;

        if (args != null) {
            for (String arg : args) {
                if (arg == null || arg.isBlank()) {
                    continue;
                }
                if ("--help".equals(arg) || "-h".equals(arg)) {
                    printUsage();
                    System.exit(0);
                } else if (arg.startsWith("--query=")) {
                    query = arg.substring("--query=".length());
                } else if (arg.startsWith("--timespan=")) {
                    timespan = arg.substring("--timespan=".length());
                } else if (arg.startsWith("--lookback=")) {
                    lookback = parseDuration(arg.substring("--lookback=".length()));
                } else if (arg.startsWith("--window=")) {
                    window = parseDuration(arg.substring("--window=".length()));
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
        }

        return new CliOptions(query, timespan, Optional.ofNullable(lookback), window);
    }

    private static Duration parseDuration(String token) {
        Matcher matcher = DURATION_PATTERN.matcher(token.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid duration '" + token
                + "'. Use formats like 30m, 2h, 7d.");
        }

        long value = Long.parseLong(matcher.group(1));
        if (value <= 0) {
            throw new IllegalArgumentException("Duration must be positive: " + token);
        }

        return switch (matcher.group(2).toLowerCase()) {
            case "s" -> Duration.ofSeconds(value);
            case "m" -> Duration.ofMinutes(value);
            case "h" -> Duration.ofHours(value);
            case "d" -> Duration.ofDays(value);
            default -> throw new IllegalStateException("Unexpected duration unit: " + matcher.group(2));
        };
    }

    private static void printUsage() {
        System.out.println("""
            Usage: java -jar news-gatherer.jar [options]

              --query=...       Override the default GDELT query
              --timespan=...    Timespan (e.g. 30m, 2h) for a single window run
              --lookback=...    Enable backfill mode covering the past duration (e.g. 7d)
              --window=...      Window size used to chunk the lookback range (default 1h)
              --help            Show this message
            """);
    }
}
