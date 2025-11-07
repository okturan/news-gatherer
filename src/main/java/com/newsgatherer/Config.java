package com.newsgatherer;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Application configuration and constants.
 */
public class Config {

    // GDELT API Configuration
    public static final String GDELT_API_ENDPOINT = "https://api.gdeltproject.org/api/v2/doc/doc";
    public static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    // GDELT API Limits (DOC 2.0 API constraints)
    public static final int GDELT_MAX_RECORDS = 250;  // Hard limit for mode=artlist
    public static final int SAFE_MAX_RECORDS = 240;   // Safety margin to avoid saturation
    public static final int MAX_ARTICLES = 200;       // Default fetch size

    // Rate Limiting (to avoid throttling)
    public static final Duration MIN_REQUEST_INTERVAL = Duration.ofMillis(2000);  // 0.5 QPS
    public static final Duration BACKOFF_BASE = Duration.ofMillis(1000);
    public static final int MAX_RETRIES = 3;

    public static final String DEFAULT_QUERY = "sourcecountry:turkey sourcelang:turkish";
    public static final String DEFAULT_TIMESPAN = "2h";
    public static final Duration MIN_TIME_SLICE = Duration.ofMinutes(15);  // GDELT minimum

    // Clustering Configuration
    public static final Duration TIME_WINDOW = Duration.ofHours(48);
    public static final double SIMILARITY_THRESHOLD = 0.80;
    public static final int SHINGLE_SIZE = 4;

    // Turkish Language Configuration
    public static final Locale TURKISH_LOCALE = Locale.of("tr", "TR");
    public static final Set<String> STOP_WORDS = Set.of(
        "son", "dakika", "video", "galeri",
        "izle", "foto", "yorum", "haber",
        "haberi", "güncel", "flas", "flaş"
    );

    // Text Processing Patterns (pre-compiled for performance)
    public static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[\\p{Punct}]+");
    public static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    // URL Canonicalization
    public static final Set<String> TRACKING_PARAMS = Set.of(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "gclid", "fbclid", "msclkid",
        "xtor", "trk", "ref", "referrer"
    );

    // Source Type Domains
    public static final Set<String> WIRE_DOMAINS = Set.of(
        "aa.com.tr", "iha.com.tr", "dha.com.tr", "anka.com.tr"
    );

    public static final Set<String> AGGREGATOR_DOMAINS = Set.of(
        "ensonhaber.com", "haberler.com", "haber7.com",
        "mynet.com", "internethaber.com", "sabah.com.tr"
    );

    // Input Validation Limits
    public static final int MAX_TITLE_LENGTH = 10_000;
    public static final int MAX_URL_LENGTH = 2_048;
}
