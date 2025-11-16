package com.newsgatherer.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.newsgatherer.config.Config;
import com.newsgatherer.domain.Article;
import com.newsgatherer.parser.ArticleParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Thin wrapper around the GDELT DOC API with built-in rate limiting.
 */
public class GdeltApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ArticleParser articleParser;
    private long lastRequestTime = 0;

    private static final DateTimeFormatter API_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    public GdeltApiClient() {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Config.HTTP_TIMEOUT)
                .build(),
            new ObjectMapper().registerModule(new JavaTimeModule()),
            new ArticleParser());
    }

    public GdeltApiClient(HttpClient httpClient, ObjectMapper objectMapper, ArticleParser articleParser) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.articleParser = articleParser;
    }

    public List<Article> fetchArticles(String query, String timespan, int maxRecords)
        throws IOException, InterruptedException {
        return executeFetch(buildUri(query, timespan, maxRecords));
    }

    public List<Article> fetchArticles(String query,
                                       ZonedDateTime start,
                                       ZonedDateTime end,
                                       int maxRecords) throws IOException, InterruptedException {
        if (!start.isBefore(end)) {
            return List.of();
        }
        return executeFetch(buildUri(query, start, end, maxRecords));
    }

    private List<Article> executeFetch(URI uri) throws IOException, InterruptedException {
        applyRateLimit();

        HttpRequest request = HttpRequest.newBuilder(uri)
            .GET()
            .header("Accept", "application/json")
            .build();

        HttpResponse<InputStream> response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofInputStream()
        );

        if (response.statusCode() != 200) {
            throw new IOException("GDELT API returned status " + response.statusCode());
        }

        try (InputStream body = response.body()) {
            JsonNode root = objectMapper.readTree(body);
            var articlesArray = articleParser.extractArticlesArray(root);

            if (articlesArray == null || articlesArray.isEmpty()) {
                return List.of();
            }
            if (articlesArray.size() >= Config.SAFE_MAX_RECORDS) {
                System.out.println("  → Hit " + articlesArray.size()
                    + " articles (near limit), results may be truncated");
            }
            return articleParser.parseArticles(articlesArray);
        }
    }

    private void applyRateLimit() throws InterruptedException {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTime;
        long minInterval = Config.MIN_REQUEST_INTERVAL.toMillis();
        if (lastRequestTime != 0 && elapsed < minInterval) {
            Thread.sleep(minInterval - elapsed);
        }
        lastRequestTime = System.currentTimeMillis();
    }

    private URI buildUri(String query, String timespan, int maxRecords) {
        String queryString = "query=" + URLEncoder.encode(query, StandardCharsets.UTF_8) +
            "&mode=artlist" +
            "&format=json" +
            "&timespan=" + URLEncoder.encode(timespan, StandardCharsets.UTF_8) +
            "&maxrecords=" + maxRecords +
            "&sort=datedesc";
        return URI.create(Config.GDELT_API_ENDPOINT + "?" + queryString);
    }

    private URI buildUri(String query, ZonedDateTime start, ZonedDateTime end, int maxRecords) {
        String queryString = "query=" + URLEncoder.encode(query, StandardCharsets.UTF_8) +
            "&mode=artlist" +
            "&format=json" +
            "&STARTDATETIME=" + API_DATE_FORMAT.format(start.withZoneSameInstant(ZoneOffset.UTC)) +
            "&ENDDATETIME=" + API_DATE_FORMAT.format(end.withZoneSameInstant(ZoneOffset.UTC)) +
            "&maxrecords=" + maxRecords +
            "&sort=datedesc";
        return URI.create(Config.GDELT_API_ENDPOINT + "?" + queryString);
    }
}
