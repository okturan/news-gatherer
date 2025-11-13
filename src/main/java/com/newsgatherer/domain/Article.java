package com.newsgatherer.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.ZonedDateTime;
import java.util.Objects;

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
    String canonicalUrl
) {
    public Article {
        Objects.requireNonNull(url, "URL cannot be null");
        Objects.requireNonNull(title, "Title cannot be null");
        Objects.requireNonNull(domain, "Domain cannot be null");
    }

    @JsonIgnore
    public ZonedDateTime getEffectiveDate() {
        return publishedDate != null ? publishedDate : seenDate;
    }
}
