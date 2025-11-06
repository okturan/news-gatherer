package com.newsgatherer.domain.model;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable domain model representing a news article.
 * Uses record pattern for automatic immutability and value semantics.
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
    /**
     * Compact constructor for validation.
     */
    public Article {
        Objects.requireNonNull(url, "URL cannot be null");
        Objects.requireNonNull(title, "Title cannot be null");
        Objects.requireNonNull(domain, "Domain cannot be null");
        Objects.requireNonNull(sourceType, "SourceType cannot be null");

        // Defensive copy for mutable collection
        if (shingles != null) {
            shingles = Set.copyOf(shingles);
        }
    }

    /**
     * Returns the effective date for this article.
     * Prefers published date, falls back to seen date.
     *
     * @return the published date if available, otherwise the seen date
     */
    public ZonedDateTime getEffectiveDate() {
        return publishedDate != null ? publishedDate : seenDate;
    }

    /**
     * Creates a new Article with the same properties except for the specified changes.
     * This is a convenience method for creating modified copies.
     */
    public static Builder from(Article article) {
        return new Builder()
            .url(article.url)
            .title(article.title)
            .domain(article.domain)
            .language(article.language)
            .sourceCountry(article.sourceCountry)
            .seenDate(article.seenDate)
            .publishedDate(article.publishedDate)
            .sourceType(article.sourceType)
            .normalizedTitle(article.normalizedTitle)
            .shingles(article.shingles)
            .canonicalUrl(article.canonicalUrl);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String url;
        private String title;
        private String domain;
        private String language;
        private String sourceCountry;
        private ZonedDateTime seenDate;
        private ZonedDateTime publishedDate;
        private SourceType sourceType;
        private String normalizedTitle;
        private Set<String> shingles;
        private String canonicalUrl;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder sourceCountry(String sourceCountry) {
            this.sourceCountry = sourceCountry;
            return this;
        }

        public Builder seenDate(ZonedDateTime seenDate) {
            this.seenDate = seenDate;
            return this;
        }

        public Builder publishedDate(ZonedDateTime publishedDate) {
            this.publishedDate = publishedDate;
            return this;
        }

        public Builder sourceType(SourceType sourceType) {
            this.sourceType = sourceType;
            return this;
        }

        public Builder normalizedTitle(String normalizedTitle) {
            this.normalizedTitle = normalizedTitle;
            return this;
        }

        public Builder shingles(Set<String> shingles) {
            this.shingles = shingles;
            return this;
        }

        public Builder canonicalUrl(String canonicalUrl) {
            this.canonicalUrl = canonicalUrl;
            return this;
        }

        public Article build() {
            return new Article(
                url, title, domain, language, sourceCountry,
                seenDate, publishedDate, sourceType,
                normalizedTitle, shingles, canonicalUrl
            );
        }
    }
}
