package com.newsgatherer.presentation.console;

import com.newsgatherer.domain.model.Article;
import com.newsgatherer.domain.model.ArticleCluster;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Formats article clusters for console output.
 */
public class ClusterFormatter {
    private static final DateTimeFormatter FULL_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SHORT_DATE_FORMAT =
        DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /**
     * Formats all clusters for console display.
     */
    public void formatClusters(List<ArticleCluster> clusters) {
        int clusterNumber = 1;
        for (ArticleCluster cluster : clusters) {
            formatCluster(cluster, clusterNumber++);
        }
    }

    /**
     * Formats a single cluster.
     */
    private void formatCluster(ArticleCluster cluster, int clusterNumber) {
        Article canonical = cluster.getCanonical();

        // Print cluster header
        String timestamp = canonical.getEffectiveDate().format(FULL_DATE_FORMAT);
        System.out.println("\n[" + clusterNumber + "] " + timestamp +
                          " • CANONICAL (" + canonical.sourceType() + ") " +
                          canonical.domain());
        System.out.println("     " + canonical.title());
        System.out.println("     " + canonical.canonicalUrl());

        // Print members if cluster has more than one article
        if (cluster.size() > 1) {
            System.out.println("     Members (" + cluster.size() + "):");
            printMembers(cluster, canonical);
        }
    }

    /**
     * Prints cluster members sorted by time.
     */
    private void printMembers(ArticleCluster cluster, Article canonical) {
        cluster.getArticles().stream()
            .sorted(Comparator.comparing(Article::getEffectiveDate))
            .forEach(article -> printMember(article, canonical));
    }

    /**
     * Prints a single cluster member.
     */
    private void printMember(Article article, Article canonical) {
        String marker = article.equals(canonical) ? "★" : "•";
        String timestamp = article.getEffectiveDate().format(SHORT_DATE_FORMAT);
        String title = truncate(article.title(), 90);

        System.out.printf("       %s %-11s %-20s %s  %s%n",
            marker,
            article.sourceType(),
            article.domain(),
            timestamp,
            title
        );
    }

    /**
     * Truncates text to specified length.
     */
    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 1) + "…";
    }
}
