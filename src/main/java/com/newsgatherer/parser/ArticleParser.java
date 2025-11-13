package com.newsgatherer.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.newsgatherer.config.Config;
import com.newsgatherer.domain.Article;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Transforms raw GDELT article payloads into {@link Article} records.
 */
public class ArticleParser {

    private static final ZoneId ISTANBUL_TZ = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter GDELT_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter GDELT_COMPACT_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    public ArrayNode extractArticlesArray(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (root.has("articles") && root.get("articles").isArray()) {
            return (ArrayNode) root.get("articles");
        }
        if (root.has("artlist") && root.get("artlist").isArray()) {
            return (ArrayNode) root.get("artlist");
        }
        return null;
    }

    public List<Article> parseArticles(ArrayNode articlesArray) {
        if (articlesArray == null || articlesArray.isEmpty()) {
            return List.of();
        }

        return StreamSupport.stream(articlesArray.spliterator(), false)
            .map(this::parseArticle)
            .filter(Objects::nonNull)
            .toList();
    }

    private Article parseArticle(JsonNode node) {
        try {
            String url = extractField(node, "url", "urlMobile", "link");
            String title = extractField(node, "title", "titleMobile");

            if (url == null || title == null) {
                return null;
            }
            if (title.length() > Config.MAX_TITLE_LENGTH || url.length() > Config.MAX_URL_LENGTH) {
                return null;
            }

            String domain = extractDomain(node, url);
            String language = extractField(node, "language", "sourcelang");
            String sourceCountry = extractField(node, "sourcecountry");
            ZonedDateTime seenDate = parseDate(extractField(node, "seendate", "date"));
            ZonedDateTime publishDate = parseDate(extractField(node, "publishdate", "published"));

            String canonicalUrl = canonicalizeUrl(url);

            return new Article(
                url, title, domain, language, sourceCountry,
                seenDate, publishDate, canonicalUrl
            );
        } catch (IllegalArgumentException e) {
            System.err.println("Skipping article due to invalid data: " + e.getMessage());
            return null;
        }
    }

    private String extractField(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            if (node.has(field) && !node.get(field).isNull()) {
                return node.get(field).asText();
            }
        }
        return null;
    }

    private String extractDomain(JsonNode node, String url) {
        String domain = node.has("domain") && !node.get("domain").isNull()
            ? node.get("domain").asText()
            : extractDomainFromUrl(url);
        if (domain == null) {
            return "";
        }
        return domain.toLowerCase(Locale.ROOT);
    }

    private String extractDomainFromUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) {
                return "";
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (URISyntaxException e) {
            return "";
        }
    }

    private ZonedDateTime parseDate(String dateString) {
        if (dateString == null) {
            return null;
        }

        try {
            if (dateString.length() == 16 && dateString.charAt(8) == 'T') {
                LocalDateTime ldt = LocalDateTime.parse(dateString, GDELT_COMPACT_FMT);
                return ldt.atZone(ZoneId.of("UTC")).withZoneSameInstant(ISTANBUL_TZ);
            }
            if (dateString.length() == 19 && dateString.charAt(10) == ' ') {
                LocalDateTime ldt = LocalDateTime.parse(dateString, GDELT_DATE_FMT);
                return ldt.atZone(ISTANBUL_TZ);
            }
            return ZonedDateTime.parse(dateString).withZoneSameInstant(ISTANBUL_TZ);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private String canonicalizeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        try {
            URI uri = new URI(url);

            String path = uri.getPath();
            if (path != null && path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            String query = uri.getQuery();
            if (query != null && !query.isEmpty()) {
                query = Arrays.stream(query.split("&"))
                    .filter(param -> {
                        String key = param.split("=")[0].toLowerCase();
                        return !key.startsWith("utm_") && !Config.TRACKING_PARAMS.contains(key);
                    })
                    .collect(Collectors.joining("&"));
                query = query.isEmpty() ? null : query;
            }

            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(),
                uri.getPort(), path, query, null).toString();

        } catch (URISyntaxException e) {
            return url;
        }
    }
}
