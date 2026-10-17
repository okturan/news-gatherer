package com.newsgatherer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.newsgatherer.client.GdeltApiClient;
import com.newsgatherer.client.GdeltApiClient.FetchResult;
import com.newsgatherer.config.CliOptions;
import com.newsgatherer.config.Config;
import com.newsgatherer.domain.Article;
import com.newsgatherer.output.ArticlePrinter;
import com.newsgatherer.storage.ArticleRepository;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lightweight CLI that fetches Turkish GDELT articles and stores
 * deduplicated results in a local SQLite database.
 */
public class GdeltNewsGatherer {

    /** Success, including a valid answer with zero articles. */
    static final int EXIT_OK = 0;
    /** GDELT answered 200 with an empty body or {}: invalid query or throttling. */
    static final int EXIT_EMPTY_RESPONSE = 3;

    static final String EMPTY_RESPONSE_WARNING =
        "Warning: GDELT returned nothing: the query may be invalid or throttled";

    private static final Duration MIN_SPLIT_WINDOW = Duration.ofMinutes(30);
    private static final DateTimeFormatter WINDOW_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final GdeltApiClient apiClient;
    private final ArticlePrinter articlePrinter;
    private final ObjectMapper objectMapper;
    private final String databaseFile;

    public GdeltNewsGatherer() {
        this(new GdeltApiClient(), new ArticlePrinter(), new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    public GdeltNewsGatherer(GdeltApiClient apiClient, ArticlePrinter articlePrinter, ObjectMapper objectMapper) {
        this(apiClient, articlePrinter, objectMapper, Config.DATABASE_FILE);
    }

    public GdeltNewsGatherer(GdeltApiClient apiClient,
                              ArticlePrinter articlePrinter,
                              ObjectMapper objectMapper,
                              String databaseFile) {
        this.databaseFile = databaseFile;
        this.apiClient = apiClient;
        this.articlePrinter = articlePrinter;
        this.objectMapper = objectMapper;
    }

    public static void main(String[] args) {
        CliOptions options = CliOptions.parse(args);
        try {
            GdeltNewsGatherer gatherer = new GdeltNewsGatherer();
            int exitCode;
            if (options.demo()) {
                gatherer.runDemo(options);
                exitCode = EXIT_OK;
            } else if (options.lookback().isPresent()) {
                exitCode = gatherer.runBackfill(options);
            } else {
                exitCode = gatherer.run(options);
            }
            if (exitCode != EXIT_OK) {
                System.exit(exitCode);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            throw new IllegalStateException("News Gatherer failed to run", e);
        }
    }

    public int run(CliOptions options) throws Exception {
        String query = options.query();
        String timespan = options.timespan();
        try (ArticleRepository repository = new ArticleRepository(databaseFile)) {
            System.out.println("Loading seen URLs...");
            Map<String, Long> seenUrls = repository.loadSeenUrls();
            System.out.println("  → Tracking " + seenUrls.size() + " previously seen URLs");

            System.out.println("Fetching articles from GDELT...");
            FetchResult fetched = apiClient.fetchArticlesWithMetadata(query, timespan, Config.MAX_ARTICLES);
            List<Article> allArticles = fetched.articles();

            if (fetched.emptyResponse()) {
                writeJsonIfRequested(List.of(), options.jsonOutput());
                System.out.println(EMPTY_RESPONSE_WARNING);
                return EXIT_EMPTY_RESPONSE;
            }
            if (allArticles.isEmpty()) {
                writeJsonIfRequested(List.of(), options.jsonOutput());
                System.out.println("No articles found.");
                return EXIT_OK;
            }

            System.out.println("Fetched " + allArticles.size() + " articles.");
            if (fetched.reachedLimit(Config.MAX_ARTICLES)) {
                System.out.println("Note: GDELT returned the " + Config.MAX_ARTICLES + "-record cap, so results for "
                    + timespan + " are truncated. Use backfill for full coverage, e.g. --lookback="
                    + timespan + " --window=1h.");
            }

            List<Article> newArticles = repository.saveNewArticles(allArticles, seenUrls);
            int filteredCount = allArticles.size() - newArticles.size();

            if (newArticles.isEmpty()) {
                writeJsonIfRequested(List.of(), options.jsonOutput());
                System.out.println("No new articles (all previously seen).");
                return EXIT_OK;
            }

            articlePrinter.printArticles(newArticles, allArticles.size(), filteredCount);

            System.out.println("Stored " + newArticles.size() + " new articles in " + databaseFile);
            writeJsonIfRequested(newArticles, options.jsonOutput());
            return EXIT_OK;
        }
    }

    /**
     * Renders a deterministic, network-free product proof using synthetic articles.
     * No database is opened and no publisher content is fetched.
     */
    public void runDemo(CliOptions options) {
        List<Article> articles = List.of(
            demoArticle(
                "https://kamu.example/haber/acik-veri",
                "İstanbul açık veri kataloğunu güncelledi",
                "kamu.example",
                ZonedDateTime.of(2026, 7, 17, 9, 15, 0, 0, ZoneOffset.UTC)
            ),
            demoArticle(
                "https://arsiv.example/kultur/arastirma",
                "Türkçe arşivler araştırmacılara açıldı",
                "arsiv.example",
                ZonedDateTime.of(2026, 7, 17, 8, 42, 0, 0, ZoneOffset.UTC)
            ),
            demoArticle(
                "https://lab.example/teknoloji/erisilebilirlik",
                "Bağımsız ekip erişilebilirlik raporunu yayımladı",
                "lab.example",
                ZonedDateTime.of(2026, 7, 17, 7, 30, 0, 0, ZoneOffset.UTC)
            )
        );
        articlePrinter.printDemoHeader();
        articlePrinter.printArticles(articles, 5, 2);
        writeJsonIfRequested(articles, options.jsonOutput());
    }

    private Article demoArticle(String url, String title, String domain, ZonedDateTime publishedDate) {
        return new Article(url, title, domain, "Turkish", "Turkey", null, publishedDate, url);
    }

    public int runBackfill(CliOptions options) throws Exception {
        Duration lookback = options.lookback().orElseThrow(() ->
            new IllegalArgumentException("lookback duration is required for backfill mode"));
        Duration window = options.window();
        validateDuration("lookback", lookback);
        validateDuration("window", window);

        try (ArticleRepository repository = new ArticleRepository(databaseFile)) {
            System.out.println("Loading seen URLs...");
            Map<String, Long> seenUrls = repository.loadSeenUrls();
            System.out.println("  → Tracking " + seenUrls.size() + " previously seen URLs");

            ZonedDateTime end = ZonedDateTime.now(ZoneOffset.UTC);
            ZonedDateTime start = end.minus(lookback);

            System.out.printf("Backfill range: %s → %s UTC (%s lookback, %s windows)%n",
                formatTimestamp(start), formatTimestamp(end), describeDuration(lookback), describeDuration(window));

            BackfillStats stats = new BackfillStats();
            ZonedDateTime cursor = start;
            while (cursor.isBefore(end)) {
                ZonedDateTime next = cursor.plus(window);
                if (next.isAfter(end)) {
                    next = end;
                }
                ingestWindow(options.query(), cursor, next, repository, seenUrls, stats);
                cursor = next;
            }

            System.out.printf("Backfill complete. Processed %d windows.%n", stats.windowsProcessed());
            int exitCode = stats.emptyResponses() > 0 ? EXIT_EMPTY_RESPONSE : EXIT_OK;
            if (exitCode != EXIT_OK) {
                System.out.printf("%s (%d window(s) came back empty).%n",
                    EMPTY_RESPONSE_WARNING, stats.emptyResponses());
            }
            if (stats.newArticles().isEmpty()) {
                writeJsonIfRequested(List.of(), options.jsonOutput());
                System.out.println("No new articles were discovered in the requested window.");
                return exitCode;
            }
            articlePrinter.printArticles(stats.newArticles(), stats.totalFetched(),
                stats.totalFetched() - stats.totalNew());
            System.out.println("Stored " + stats.totalNew() + " new articles in " + databaseFile);
            writeJsonIfRequested(stats.newArticles(), options.jsonOutput());
            return exitCode;
        }
    }

    private void ingestWindow(String query,
                              ZonedDateTime start,
                              ZonedDateTime end,
                              ArticleRepository repository,
                              Map<String, Long> seenUrls,
                              BackfillStats stats) throws Exception {
        FetchResult result = fetchWindowArticles(query, start, end);
        if (result.emptyResponse()) {
            System.out.println("  → " + EMPTY_RESPONSE_WARNING);
            stats.incrementWindows();
            stats.incrementEmptyResponses();
            return;
        }
        List<Article> articles = result.articles();
        Duration span = Duration.between(start, end);

        if (shouldSplitWindow(result, span)) {
            splitWindow(query, start, end, repository, seenUrls, stats, result.responseCount());
            return;
        }

        stats.incrementWindows();
        if (articles.isEmpty()) {
            return;
        }

        stats.addFetched(articles.size());
        persistNewArticles(articles, result.responseCount(), start, end, repository, seenUrls, stats);
    }

    private FetchResult fetchWindowArticles(String query,
                                            ZonedDateTime start,
                                            ZonedDateTime end) throws Exception {
        System.out.printf("Window %s → %s UTC%n", formatTimestamp(start), formatTimestamp(end));
        return apiClient.fetchArticlesWithMetadata(query, start, end, Config.MAX_ARTICLES);
    }

    static boolean shouldSplitWindow(FetchResult result, Duration span) {
        if (!result.reachedLimit(Config.MAX_ARTICLES)) {
            return false;
        }
        return span.compareTo(MIN_SPLIT_WINDOW) > 0;
    }

    private void splitWindow(String query,
                             ZonedDateTime start,
                             ZonedDateTime end,
                             ArticleRepository repository,
                             Map<String, Long> seenUrls,
                             BackfillStats stats,
                             int articleCount) throws Exception {
        System.out.println("  → Window saturated (" + articleCount
            + ") - splitting into smaller slices");
        ZonedDateTime midpoint = start.plus(Duration.between(start, end).dividedBy(2));
        if (!midpoint.isAfter(start)) {
            midpoint = start.plusMinutes(1);
        }
        ingestWindow(query, start, midpoint, repository, seenUrls, stats);
        ingestWindow(query, midpoint, end, repository, seenUrls, stats);
    }

    private void persistNewArticles(List<Article> articles,
                                    int responseCount,
                                    ZonedDateTime start,
                                    ZonedDateTime end,
                                    ArticleRepository repository,
                                    Map<String, Long> seenUrls,
                                    BackfillStats stats) throws Exception {
        Duration span = Duration.between(start, end);
        if (responseCount >= Config.MAX_ARTICLES && span.compareTo(MIN_SPLIT_WINDOW) <= 0) {
            System.out.println("  → Warning: window hit the request limit and cannot be split below "
                + MIN_SPLIT_WINDOW.toMinutes() + " minutes. Some results may be truncated.");
        }

        List<Article> newArticles = repository.saveNewArticles(articles, seenUrls);
        if (newArticles.isEmpty()) {
            return;
        }

        stats.addNewArticles(newArticles);
    }

    private void validateDuration(String label, Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(label + " duration must be positive");
        }
    }

    private String formatTimestamp(ZonedDateTime value) {
        return WINDOW_FORMAT.format(value);
    }

    private String describeDuration(Duration duration) {
        long totalMinutes = duration.toMinutes();
        long days = totalMinutes / (24 * 60);
        long hours = (totalMinutes % (24 * 60)) / 60;
        long minutes = totalMinutes % 60;
        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append("d ");
        }
        if (hours > 0) {
            builder.append(hours).append("h ");
        }
        if (minutes > 0 || builder.length() == 0) {
            builder.append(minutes).append("m");
        }
        return builder.toString().trim();
    }

    private void writeJsonIfRequested(List<Article> articles, Optional<Path> destination) {
        if (destination.isEmpty()) {
            return;
        }
        Path output = destination.get();
        try (BufferedWriter writer = Files.newBufferedWriter(
            output,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )) {
            for (Article article : articles) {
                writer.write(objectMapper.writeValueAsString(article));
                writer.newLine();
            }
            System.out.printf("Wrote %d article(s) to %s%n", articles.size(), output);
        } catch (IOException e) {
            System.err.println("Failed to write JSON output to " + output + ": " + e.getMessage());
        }
    }

    private static final class BackfillStats {
        private final List<Article> newArticles = new ArrayList<>();
        private long totalFetched = 0;
        private long totalNew = 0;
        private int windowsProcessed = 0;
        private int emptyResponses = 0;

        void incrementEmptyResponses() {
            emptyResponses++;
        }

        int emptyResponses() {
            return emptyResponses;
        }

        void incrementWindows() {
            windowsProcessed++;
        }

        void addFetched(int count) {
            totalFetched += count;
        }

        void addNewArticles(List<Article> articles) {
            totalNew += articles.size();
            newArticles.addAll(articles);
        }

        List<Article> newArticles() {
            return newArticles;
        }

        long totalFetched() {
            return totalFetched;
        }

        long totalNew() {
            return totalNew;
        }

        int windowsProcessed() {
            return windowsProcessed;
        }
    }
}
