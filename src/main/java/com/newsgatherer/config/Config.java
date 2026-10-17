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
    public static final int MAX_ARTICLES = 200;       // Default fetch size and saturation threshold

    // Rate Limiting. GDELT answers 429 with "limit requests to one every 5 seconds".
    public static final Duration MIN_REQUEST_INTERVAL = Env.durationFromMillis(
        "NEWS_GATHERER_MIN_REQUEST_INTERVAL_MS",
        Duration.ofMillis(5000)
    );  // 0.2 QPS default

    // Retry policy for HTTP 429 and 5xx responses
    public static final int MAX_ATTEMPTS = Env.intOrDefault("NEWS_GATHERER_MAX_ATTEMPTS", 4);
    public static final Duration RETRY_BASE_DELAY = Env.durationFromMillis(
        "NEWS_GATHERER_RETRY_BASE_DELAY_MS",
        Duration.ofSeconds(5)
    );
    public static final Duration RETRY_MAX_DELAY = Duration.ofSeconds(120);

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
    public static final String OUTPUT_DIR = Env.stringOrDefault("NEWS_GATHERER_OUTPUT_DIR", "output");
    public static final String DATABASE_FILE = Env.stringOrDefault(
        "NEWS_GATHERER_DATABASE_FILE",
        Paths.get(OUTPUT_DIR, "news-gatherer.db").toString()
    );
    public static final Duration SEEN_URL_RETENTION = Env.durationFromDays(
        "NEWS_GATHERER_SEEN_RETENTION_DAYS",
        Duration.ofDays(7)
    );  // Forget URLs after 7 days
}
