package com.newsgatherer.infrastructure.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.newsgatherer.config.ClusteringConfig;
import com.newsgatherer.config.TurkishLanguageConfig;
import com.newsgatherer.domain.model.Article;
import com.newsgatherer.domain.model.SourceType;
import com.newsgatherer.infrastructure.text.DomainExtractor;
import com.newsgatherer.infrastructure.text.ShingleGenerator;
import com.newsgatherer.infrastructure.text.TurkishTextNormalizer;
import com.newsgatherer.infrastructure.text.UrlCanonicalizer;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses GDELT API JSON responses into Article domain objects.
 * Uses Java 21 pattern matching for cleaner JSON field extraction.
 */
public class GdeltArticleParser {
    private static final ZoneId ISTANBUL_TIMEZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter GDELT_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TurkishTextNormalizer textNormalizer;
    private final ShingleGenerator shingleGenerator;
    private final UrlCanonicalizer urlCanonicalizer;
    private final DomainExtractor domainExtractor;

    public GdeltArticleParser(
            TurkishLanguageConfig languageConfig,
            ClusteringConfig clusteringConfig) {
        this.textNormalizer = new TurkishTextNormalizer(languageConfig);
        this.shingleGenerator = new ShingleGenerator(clusteringConfig.getShingleSize());
        this.urlCanonicalizer = new UrlCanonicalizer();
        this.domainExtractor = new DomainExtractor();
    }

    /**
     * Parses GDELT JSON response into list of Article objects.
     *
     * @param jsonResponse the JSON response from GDELT API
     * @return list of parsed articles
     */
    public List<Article> parseArticles(JsonNode jsonResponse) {
        ArrayNode articlesArray = extractArticlesArray((ObjectNode) jsonResponse);

        List<Article> articles = new ArrayList<>();
        for (JsonNode node : articlesArray) {
            try {
                Article article = parseArticle(node);
                if (article != null) {
                    articles.add(article);
                }
            } catch (Exception e) {
                // Skip invalid articles
            }
        }

        return articles;
    }

    /**
     * Extracts the articles array from JSON response.
     * GDELT API sometimes returns "articles" or "artlist" field.
     */
    private ArrayNode extractArticlesArray(ObjectNode root) {
        return switch (root) {
            case ObjectNode n when n.has("articles") && n.get("articles").isArray()
                -> (ArrayNode) n.get("articles");
            case ObjectNode n when n.has("artlist") && n.get("artlist").isArray()
                -> (ArrayNode) n.get("artlist");
            default -> null;
        };
    }

    /**
     * Parses a single article from JSON node.
     */
    private Article parseArticle(JsonNode node) {
        String url = extractUrl(node);
        String title = extractTitle(node);

        if (url == null || title == null) {
            return null;
        }

        String domain = extractDomain(node, url);
        String normalizedTitle = textNormalizer.normalize(title);

        return Article.builder()
            .url(url)
            .title(title)
            .domain(domain)
            .language(extractLanguage(node))
            .sourceCountry(extractSourceCountry(node))
            .seenDate(extractSeenDate(node))
            .publishedDate(extractPublishDate(node))
            .sourceType(SourceType.fromDomain(domain))
            .normalizedTitle(normalizedTitle)
            .shingles(shingleGenerator.generate(normalizedTitle))
            .canonicalUrl(urlCanonicalizer.canonicalize(url))
            .build();
    }

    /**
     * Extracts URL using pattern matching.
     * GDELT may provide "url", "urlMobile", or "link" fields.
     */
    private String extractUrl(JsonNode node) {
        return switch (node) {
            case JsonNode n when n.has("url") && !n.get("url").isNull()
                -> n.get("url").asText();
            case JsonNode n when n.has("urlMobile") && !n.get("urlMobile").isNull()
                -> n.get("urlMobile").asText();
            case JsonNode n when n.has("link") && !n.get("link").isNull()
                -> n.get("link").asText();
            default -> null;
        };
    }

    /**
     * Extracts title using pattern matching.
     */
    private String extractTitle(JsonNode node) {
        return switch (node) {
            case JsonNode n when n.has("title") && !n.get("title").isNull()
                -> n.get("title").asText();
            case JsonNode n when n.has("titleMobile") && !n.get("titleMobile").isNull()
                -> n.get("titleMobile").asText();
            default -> null;
        };
    }

    /**
     * Extracts domain, either from JSON or from URL.
     */
    private String extractDomain(JsonNode node, String url) {
        if (node.has("domain") && !node.get("domain").isNull()) {
            return node.get("domain").asText().toLowerCase();
        }
        return domainExtractor.extractDomain(url);
    }

    /**
     * Extracts language field.
     */
    private String extractLanguage(JsonNode node) {
        return switch (node) {
            case JsonNode n when n.has("language") && !n.get("language").isNull()
                -> n.get("language").asText();
            case JsonNode n when n.has("sourcelang") && !n.get("sourcelang").isNull()
                -> n.get("sourcelang").asText();
            default -> null;
        };
    }

    /**
     * Extracts source country field.
     */
    private String extractSourceCountry(JsonNode node) {
        if (node.has("sourcecountry") && !node.get("sourcecountry").isNull()) {
            return node.get("sourcecountry").asText();
        }
        return null;
    }

    /**
     * Extracts seen date.
     */
    private ZonedDateTime extractSeenDate(JsonNode node) {
        return switch (node) {
            case JsonNode n when n.has("seendate") && !n.get("seendate").isNull()
                -> parseDateTime(n.get("seendate").asText());
            case JsonNode n when n.has("date") && !n.get("date").isNull()
                -> parseDateTime(n.get("date").asText());
            default -> null;
        };
    }

    /**
     * Extracts publish date.
     */
    private ZonedDateTime extractPublishDate(JsonNode node) {
        return switch (node) {
            case JsonNode n when n.has("publishdate") && !n.get("publishdate").isNull()
                -> parseDateTime(n.get("publishdate").asText());
            case JsonNode n when n.has("published") && !n.get("published").isNull()
                -> parseDateTime(n.get("published").asText());
            default -> null;
        };
    }

    /**
     * Parses GDELT date string to ZonedDateTime.
     */
    private ZonedDateTime parseDateTime(String dateString) {
        if (dateString == null) {
            return null;
        }

        try {
            // GDELT format: "yyyy-MM-dd HH:mm:ss"
            if (dateString.length() == 19 && dateString.charAt(10) == ' ') {
                LocalDateTime ldt = LocalDateTime.parse(dateString, GDELT_DATE_FORMAT);
                return ldt.atZone(ISTANBUL_TIMEZONE);
            }
            // ISO format fallback
            return ZonedDateTime.parse(dateString).withZoneSameInstant(ISTANBUL_TIMEZONE);
        } catch (Exception e) {
            return null;
        }
    }
}
