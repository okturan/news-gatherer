package com.newsgatherer.infrastructure.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.newsgatherer.domain.model.Article;
import com.newsgatherer.domain.repository.ArticleRepository;
import com.newsgatherer.domain.repository.ArticleRepositoryException;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of ArticleRepository that fetches from GDELT API.
 */
public class GdeltArticleRepository implements ArticleRepository {
    private final GdeltApiClient apiClient;
    private final GdeltArticleParser articleParser;

    public GdeltArticleRepository(
            GdeltApiClient apiClient,
            GdeltArticleParser articleParser) {
        this.apiClient = apiClient;
        this.articleParser = articleParser;
    }

    @Override
    public List<Article> findArticles(String query, String timespan, int maxRecords)
            throws ArticleRepositoryException {
        try {
            String queryString = buildQueryString(query, timespan, maxRecords);
            JsonNode jsonResponse = apiClient.fetchArticles(queryString);
            return articleParser.parseArticles(jsonResponse);
        } catch (Exception e) {
            throw new ArticleRepositoryException("Failed to fetch articles", e);
        }
    }

    /**
     * Builds GDELT API query string.
     */
    private String buildQueryString(String query, String timespan, int maxRecords) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("query", query);
        params.put("mode", "artlist");
        params.put("format", "json");
        params.put("timespan", timespan);
        params.put("maxrecords", String.valueOf(maxRecords));
        params.put("sort", "datedesc");

        return params.entrySet().stream()
            .map(this::encodeParameter)
            .collect(Collectors.joining("&"));
    }

    /**
     * URL-encodes a query parameter.
     */
    private String encodeParameter(Map.Entry<String, String> entry) {
        try {
            return URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) +
                   "=" +
                   URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode parameter", e);
        }
    }
}
