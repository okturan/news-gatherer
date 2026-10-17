package com.newsgatherer.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.newsgatherer.config.Config;
import com.newsgatherer.domain.Article;
import com.newsgatherer.parser.ArticleParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Thin wrapper around the GDELT DOC API with built-in rate limiting.
 */
public class GdeltApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ArticleParser articleParser;
    private final String endpoint;
    private final Duration minInterval;
    private final RetryPolicy retryPolicy;
    private final Sleeper sleeper;
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
        this(httpClient, objectMapper, articleParser, Config.GDELT_API_ENDPOINT, Config.MIN_REQUEST_INTERVAL,
            RetryPolicy.fromConfig(), Thread::sleep);
    }

    /** Fully injectable constructor; tests point it at a local server and a fake clock. */
    GdeltApiClient(HttpClient httpClient,
                   ObjectMapper objectMapper,
                   ArticleParser articleParser,
                   String endpoint,
                   Duration minInterval,
                   RetryPolicy retryPolicy,
                   Sleeper sleeper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.articleParser = articleParser;
        this.endpoint = endpoint;
        this.minInterval = minInterval;
        this.retryPolicy = retryPolicy;
        this.sleeper = sleeper;
    }

    public List<Article> fetchArticles(String query, String timespan, int maxRecords)
        throws IOException, InterruptedException {
        return fetchArticlesWithMetadata(query, timespan, maxRecords).articles();
    }

    public FetchResult fetchArticlesWithMetadata(String query, String timespan, int maxRecords)
        throws IOException, InterruptedException {
        return executeFetch(buildUri(query, timespan, maxRecords));
    }

    public List<Article> fetchArticles(String query,
                                       ZonedDateTime start,
                                       ZonedDateTime end,
                                       int maxRecords) throws IOException, InterruptedException {
        return fetchArticlesWithMetadata(query, start, end, maxRecords).articles();
    }

    public FetchResult fetchArticlesWithMetadata(String query,
                                                  ZonedDateTime start,
                                                  ZonedDateTime end,
                                                  int maxRecords) throws IOException, InterruptedException {
        if (!start.isBefore(end)) {
            return new FetchResult(List.of(), 0);
        }
        return executeFetch(buildUri(query, start, end, maxRecords));
    }

    private FetchResult executeFetch(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .GET()
            .header("Accept", "application/json")
            .build();

        Duration waitedTotal = Duration.ZERO;
        for (int attempt = 1; ; attempt++) {
            applyRateLimit();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status == 200) {
                return parseBody(response.body());
            }
            if (!isRetryable(status) || attempt >= retryPolicy.maxAttempts()) {
                throw new GdeltApiException(status, attempt, waitedTotal);
            }
            Duration wait = retryPolicy.delayFor(attempt, retryAfter(response));
            System.err.printf("  → GDELT returned %d; waiting %.1fs before retry %d/%d%n",
                status, wait.toMillis() / 1000.0, attempt, retryPolicy.maxAttempts() - 1);
            sleeper.sleep(wait.toMillis());
            waitedTotal = waitedTotal.plus(wait);
        }
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    private static Optional<Duration> retryAfter(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After").flatMap(GdeltApiClient::parseRetryAfter);
    }

    static Optional<Duration> parseRetryAfter(String value) {
        String trimmed = value.trim();
        try {
            return Optional.of(Duration.ofSeconds(Math.max(0, Long.parseLong(trimmed))));
        } catch (NumberFormatException notSeconds) {
            try {
                ZonedDateTime when = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
                Duration delta = Duration.between(ZonedDateTime.now(ZoneOffset.UTC), when);
                return Optional.of(delta.isNegative() ? Duration.ZERO : delta);
            } catch (DateTimeParseException notDate) {
                return Optional.empty();
            }
        }
    }

    private FetchResult parseBody(byte[] body) throws IOException {
        String text = new String(body, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            return FetchResult.nothingReturned();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(text);
        } catch (JsonProcessingException e) {
            String snippet = text.length() > 200 ? text.substring(0, 200) + "..." : text;
            throw new IOException("GDELT returned a non-JSON reply (invalid query?): " + snippet, e);
        }
        if (root == null || (root.isObject() && root.isEmpty())) {
            return FetchResult.nothingReturned();
        }
        var articlesArray = articleParser.extractArticlesArray(root);
        if (articlesArray == null || articlesArray.isEmpty()) {
            return new FetchResult(List.of(), 0);
        }
        return toFetchResult(articlesArray);
    }

    FetchResult toFetchResult(ArrayNode articlesArray) {
        return new FetchResult(articleParser.parseArticles(articlesArray), articlesArray.size());
    }

    static String requestLimitWarning(int articleCount, int maxRecords) {
        if (maxRecords <= 0 || articleCount < maxRecords) {
            return null;
        }
        return "  → Hit " + articleCount
            + " articles (request limit), results may be truncated";
    }

    /**
     * Parsed articles plus the raw API row count used to detect response saturation.
     * {@code emptyResponse} marks a 200 with an empty body or {@code {}}, which GDELT sends for
     * throttled or invalid queries, as opposed to a valid answer with zero articles.
     */
    public record FetchResult(List<Article> articles, int responseCount, boolean emptyResponse) {
        public FetchResult {
            articles = List.copyOf(Objects.requireNonNull(articles, "articles cannot be null"));
            if (responseCount < articles.size()) {
                throw new IllegalArgumentException("responseCount cannot be smaller than parsed article count");
            }
        }

        public FetchResult(List<Article> articles, int responseCount) {
            this(articles, responseCount, false);
        }

        static FetchResult nothingReturned() {
            return new FetchResult(List.of(), 0, true);
        }

        public boolean reachedLimit(int maxRecords) {
            return maxRecords > 0 && responseCount >= maxRecords;
        }
    }

    /** Sleep seam so tests never wait in real time. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /** Raised when GDELT keeps answering with a retryable status until attempts run out. */
    public static final class GdeltApiException extends IOException {
        private static final long serialVersionUID = 1L;
        private final int status;

        GdeltApiException(int status, int attempts, Duration waited) {
            super("GDELT API returned status " + status + " after " + attempts + " attempt(s), waited "
                + waited.toSeconds() + "s in total"
                + (status == 429
                    ? "; GDELT allows one request every 5 seconds. Try again later or raise "
                        + "NEWS_GATHERER_MIN_REQUEST_INTERVAL_MS."
                    : "."));
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    /**
     * Retry settings: exponential backoff from {@code baseDelay} with up to 25% jitter,
     * capped at {@code maxDelay}. A server-sent Retry-After replaces the computed delay.
     */
    record RetryPolicy(int maxAttempts, Duration baseDelay, Duration maxDelay, DoubleSupplier jitter) {
        static RetryPolicy fromConfig() {
            return new RetryPolicy(Config.MAX_ATTEMPTS, Config.RETRY_BASE_DELAY, Config.RETRY_MAX_DELAY,
                () -> ThreadLocalRandom.current().nextDouble());
        }

        Duration delayFor(int attempt, Optional<Duration> retryAfter) {
            if (retryAfter.isPresent()) {
                return retryAfter.get().compareTo(maxDelay) > 0 ? maxDelay : retryAfter.get();
            }
            long exponential = baseDelay.toMillis() << Math.min(attempt - 1, 20);
            long jittered = exponential + (long) (exponential * 0.25 * jitter.getAsDouble());
            return Duration.ofMillis(Math.min(jittered, maxDelay.toMillis()));
        }
    }

    private void applyRateLimit() throws InterruptedException {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTime;
        long minIntervalMillis = minInterval.toMillis();
        if (lastRequestTime != 0 && elapsed < minIntervalMillis) {
            sleeper.sleep(minIntervalMillis - elapsed);
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
        return URI.create(endpoint + "?" + queryString);
    }

    private URI buildUri(String query, ZonedDateTime start, ZonedDateTime end, int maxRecords) {
        String queryString = "query=" + URLEncoder.encode(query, StandardCharsets.UTF_8) +
            "&mode=artlist" +
            "&format=json" +
            "&STARTDATETIME=" + API_DATE_FORMAT.format(start.withZoneSameInstant(ZoneOffset.UTC)) +
            "&ENDDATETIME=" + API_DATE_FORMAT.format(end.withZoneSameInstant(ZoneOffset.UTC)) +
            "&maxrecords=" + maxRecords +
            "&sort=datedesc";
        return URI.create(endpoint + "?" + queryString);
    }
}
