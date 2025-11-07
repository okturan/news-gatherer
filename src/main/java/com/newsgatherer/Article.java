package com.newsgatherer;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable news article record.
 */
public record Article(
    String url,
    String title,
    String domain,
    String language,
    String sourceCountry,
    ZonedDateTime seenDate,
    ZonedDateTime publishedDate,
    SourceType sourceType,
    String normalizedTitle,
    Set<String> shingles,
    String canonicalUrl
) {
    public Article {
        Objects.requireNonNull(url, "URL cannot be null");
        Objects.requireNonNull(title, "Title cannot be null");
        Objects.requireNonNull(domain, "Domain cannot be null");
        Objects.requireNonNull(sourceType, "SourceType cannot be null");
    }

    public ZonedDateTime getEffectiveDate() {
        return publishedDate != null ? publishedDate : seenDate;
    }
}
