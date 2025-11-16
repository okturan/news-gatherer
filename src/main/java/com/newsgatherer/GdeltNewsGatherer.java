package com.newsgatherer;

import com.newsgatherer.client.GdeltApiClient;
import com.newsgatherer.config.CliOptions;
import com.newsgatherer.config.Config;
import com.newsgatherer.domain.Article;
import com.newsgatherer.output.ArticlePrinter;
import com.newsgatherer.storage.ArticleRepository;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lightweight CLI that fetches Turkish GDELT articles and stores
 * deduplicated results in a local SQLite database.
 */
public class GdeltNewsGatherer {

    private static final Duration MIN_SPLIT_WINDOW = Duration.ofMinutes(30);
    private static final DateTimeFormatter WINDOW_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final GdeltApiClient apiClient;
    private final ArticlePrinter articlePrinter;

    public GdeltNewsGatherer() {
        this(new GdeltApiClient(), new ArticlePrinter());
    }

    public GdeltNewsGatherer(GdeltApiClient apiClient, ArticlePrinter articlePrinter) {
        this.apiClient = apiClient;
        this.articlePrinter = articlePrinter;
    }

    public static void main(String[] args) {
        CliOptions options = CliOptions.parse(args);
        try {
            GdeltNewsGatherer gatherer = new GdeltNewsGatherer();
            if (options.lookback().isPresent()) {
                gatherer.runBackfill(options.query(), options.lookback().get(), options.window());
            } else {
                gatherer.run(options.query(), options.timespan());
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            throw new IllegalStateException("News Gatherer failed to run", e);
        }
    }

    public void run(String query, String timespan) throws Exception {
        try (ArticleRepository repository = new ArticleRepository(Config.DATABASE_FILE)) {
            System.out.println("Loading seen URLs...");
            Map<String, Long> seenUrls = repository.loadSeenUrls();
            System.out.println("  → Tracking " + seenUrls.size() + " previously seen URLs");

            System.out.println("Fetching articles from GDELT...");
            List<Article> allArticles = apiClient.fetchArticles(query, timespan, Config.MAX_ARTICLES);

            if (allArticles.isEmpty()) {
                System.out.println("No articles found.");
                return;
            }

            System.out.println("Fetched " + allArticles.size() + " articles.");

            List<Article> newArticles = repository.filterNewArticles(allArticles, seenUrls);
            int filteredCount = allArticles.size() - newArticles.size();

            if (newArticles.isEmpty()) {
                System.out.println("No new articles (all previously seen).");
                return;
            }

            repository.saveArticles(newArticles);
            articlePrinter.printArticles(newArticles, allArticles.size(), filteredCount);

            System.out.println("Stored " + newArticles.size() + " new articles in " + Config.DATABASE_FILE);
        }
    }

    public void runBackfill(String query, Duration lookback, Duration window) throws Exception {
        validateDuration("lookback", lookback);
        validateDuration("window", window);

        try (ArticleRepository repository = new ArticleRepository(Config.DATABASE_FILE)) {
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
                ingestWindow(query, cursor, next, repository, seenUrls, stats);
                cursor = next;
            }

            System.out.printf("Backfill complete. Processed %d windows.%n", stats.windowsProcessed());
            if (stats.newArticles().isEmpty()) {
                System.out.println("No new articles were discovered in the requested window.");
                return;
            }
            articlePrinter.printArticles(stats.newArticles(), stats.totalFetched(),
                stats.totalFetched() - stats.totalNew());
            System.out.println("Stored " + stats.totalNew() + " new articles in " + Config.DATABASE_FILE);
        }
    }

    private void ingestWindow(String query,
                              ZonedDateTime start,
                              ZonedDateTime end,
                              ArticleRepository repository,
                              Map<String, Long> seenUrls,
                              BackfillStats stats) throws Exception {
        System.out.printf("Window %s → %s UTC%n", formatTimestamp(start), formatTimestamp(end));
        List<Article> articles = apiClient.fetchArticles(query, start, end, Config.MAX_ARTICLES);

        if (shouldSplitWindow(articles.size(), start, end)) {
            System.out.println("  → Window saturated (" + articles.size()
                + ") - splitting into smaller slices");
            ZonedDateTime midpoint = start.plus(Duration.between(start, end).dividedBy(2));
            if (!midpoint.isAfter(start)) {
                midpoint = start.plusMinutes(1);
            }
            ingestWindow(query, start, midpoint, repository, seenUrls, stats);
            ingestWindow(query, midpoint, end, repository, seenUrls, stats);
            return;
        }

        stats.incrementWindows();
        if (articles.isEmpty()) {
            return;
        }

        stats.addFetched(articles.size());
        if (articles.size() >= Config.MAX_ARTICLES
            && Duration.between(start, end).compareTo(MIN_SPLIT_WINDOW) <= 0) {
            System.out.println("  → Warning: window hit the API cap and cannot be split below "
                + MIN_SPLIT_WINDOW.toMinutes() + " minutes. Some results may be truncated.");
        }
        List<Article> newArticles = repository.filterNewArticles(articles, seenUrls);
        if (newArticles.isEmpty()) {
            return;
        }

        repository.saveArticles(newArticles);
        stats.addNewArticles(newArticles);
    }

    private boolean shouldSplitWindow(int articleCount, ZonedDateTime start, ZonedDateTime end) {
        if (articleCount < Config.MAX_ARTICLES) {
            return false;
        }
        Duration span = Duration.between(start, end);
        return span.compareTo(MIN_SPLIT_WINDOW) > 0;
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

    private static final class BackfillStats {
        private final List<Article> newArticles = new ArrayList<>();
        private long totalFetched = 0;
        private long totalNew = 0;
        private int windowsProcessed = 0;

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
