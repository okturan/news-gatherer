package com.newsgatherer.output;

import com.newsgatherer.domain.Article;

import java.io.PrintStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Formats newly gathered articles and run-level totals for the console.
 */
public final class ArticlePrinter {

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
    private static final Comparator<Article> DISPLAY_ORDER = Comparator
        .comparing(Article::getEffectiveDate, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(Article::domain, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(Article::domain)
        .thenComparing(Article::title, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(Article::title)
        .thenComparing(Article::url);

    private final PrintStream output;

    /**
     * Creates a printer that writes to the process console.
     */
    public ArticlePrinter() {
        this(System.out);
    }

    /**
     * Creates a printer backed by an injectable stream for callers and tests.
     *
     * @param output destination for formatted output
     */
    public ArticlePrinter(PrintStream output) {
        this.output = Objects.requireNonNull(output, "output cannot be null");
    }

    /**
     * Prints articles newest-first without mutating the supplied list.
     *
     * @param articles newly gathered articles
     * @param totalFetched number returned by the upstream API
     * @param duplicateCount number omitted because they were already seen
     */
    public void printArticles(List<Article> articles, long totalFetched, long duplicateCount) {
        Objects.requireNonNull(articles, "articles cannot be null");
        requireNonNegative("totalFetched", totalFetched);
        requireNonNegative("duplicateCount", duplicateCount);

        List<Article> sortedArticles = articles.stream().sorted(DISPLAY_ORDER).toList();
        for (int index = 0; index < sortedArticles.size(); index++) {
            printArticle(index + 1, sortedArticles.get(index));
        }
        printSummary(totalFetched, sortedArticles.size(), duplicateCount);
    }

    private void printArticle(int index, Article article) {
        Objects.requireNonNull(article, "articles cannot contain null values");
        output.printf(Locale.ROOT, "[%03d] %s • %s%n", index, formatDate(article.getEffectiveDate()), article.domain());
        output.printf("      %s%n", article.title());
        output.printf("      %s%n%n", displayUrl(article));
    }

    private void printSummary(long totalFetched, int newArticleCount, long duplicateCount) {
        output.println("=== SUMMARY ===");
        output.printf(Locale.ROOT, "Fetched:      %d%n", totalFetched);
        output.printf(Locale.ROOT, "New articles: %d%n", newArticleCount);
        output.printf(Locale.ROOT, "Duplicates:   %d%n", duplicateCount);
        output.println();
    }

    private String formatDate(ZonedDateTime date) {
        return date == null ? "NO DATE" : date.format(DATE_FORMAT);
    }

    private String displayUrl(Article article) {
        return article.canonicalUrl() == null ? article.url() : article.canonicalUrl();
    }

    private void requireNonNegative(String label, long value) {
        if (value < 0) {
            throw new IllegalArgumentException(label + " cannot be negative");
        }
    }
}
