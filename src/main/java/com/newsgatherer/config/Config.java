package com.newsgatherer.config;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Set;

/**
 * Application configuration and constants.
 */
public class Config {

    // GDELT API Configuration
    public static final String GDELT_API_ENDPOINT = "https://api.gdeltproject.org/api/v2/doc/doc";
    public static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    // GDELT API Limits (DOC 2.0 API constraints)
    public static final int SAFE_MAX_RECORDS = 240;   // Safety margin to avoid saturation
    public static final int MAX_ARTICLES = 200;       // Default fetch size

    // Rate Limiting (to avoid throttling)
    public static final Duration MIN_REQUEST_INTERVAL = durationFromMillisEnv(
        "NEWS_GATHERER_MIN_REQUEST_INTERVAL_MS",
        Duration.ofMillis(2000)
    );  // 0.5 QPS default

    public static final String DEFAULT_QUERY = "sourcecountry:turkey sourcelang:turkish";
    public static final String DEFAULT_TIMESPAN = "2h";

    // URL Canonicalization
    public static final Set<String> TRACKING_PARAMS = Set.of(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "gclid", "fbclid", "msclkid",
        "xtor", "trk", "ref", "referrer"
    );

    // Input Validation Limits
    public static final int MAX_TITLE_LENGTH = 10_000;
    public static final int MAX_URL_LENGTH = 2_048;

    // Storage Configuration
    public static final String OUTPUT_DIR = envOrDefault("NEWS_GATHERER_OUTPUT_DIR", "output");
    public static final String DATABASE_FILE = envOrDefault(
        "NEWS_GATHERER_DATABASE_FILE",
        Paths.get(OUTPUT_DIR, "news-gatherer.db").toString()
    );
    public static final Duration SEEN_URL_RETENTION = durationFromDaysEnv(
        "NEWS_GATHERER_SEEN_RETENTION_DAYS",
        Duration.ofDays(7)
    );  // Forget URLs after 7 days

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static Duration durationFromMillisEnv(String key, Duration fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            long millis = Long.parseLong(value.trim());
            if (millis <= 0) {
                System.err.printf("[%s] must be positive; using default %d ms%n", key, fallback.toMillis());
                return fallback;
            }
            return Duration.ofMillis(millis);
        } catch (NumberFormatException ex) {
            System.err.printf("Invalid %s value '%s'; using default %d ms%n", key, value, fallback.toMillis());
            return fallback;
        }
    }

    private static Duration durationFromDaysEnv(String key, Duration fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            long days = Long.parseLong(value.trim());
            if (days <= 0) {
                System.err.printf("[%s] must be positive; using default %d days%n", key, fallback.toDays());
                return fallback;
            }
            return Duration.ofDays(days);
        } catch (NumberFormatException ex) {
            System.err.printf("Invalid %s value '%s'; using default %d days%n", key, value, fallback.toDays());
            return fallback;
        }
    }
}
