package com.newsgatherer;

import com.newsgatherer.client.GdeltApiClient;
import com.newsgatherer.config.Config;
import com.newsgatherer.domain.Article;
import com.newsgatherer.output.ArticlePrinter;
import com.newsgatherer.storage.ArticleRepository;

import java.util.List;
import java.util.Map;

/**
 * Lightweight CLI that fetches Turkish GDELT articles and stores
 * deduplicated results in a local SQLite database.
 */
public class GdeltNewsGatherer {

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
        try {
            new GdeltNewsGatherer().run(Config.DEFAULT_QUERY, Config.DEFAULT_TIMESPAN);
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
}
