package com.newsgatherer.application.service;

import com.newsgatherer.config.ClusteringConfig;
import com.newsgatherer.domain.model.Article;
import com.newsgatherer.domain.model.ArticleCluster;
import com.newsgatherer.domain.service.CanonicalSelectionService;
import com.newsgatherer.domain.service.ClusteringService;

import java.util.Comparator;
import java.util.List;

/**
 * Application service for deduplicating and clustering articles.
 */
public class ArticleDeduplicationService {
    private final ClusteringService clusteringService;
    private final CanonicalSelectionService canonicalSelectionService;
    private final ClusteringConfig config;

    public ArticleDeduplicationService(
            ClusteringService clusteringService,
            CanonicalSelectionService canonicalSelectionService,
            ClusteringConfig config) {
        this.clusteringService = clusteringService;
        this.canonicalSelectionService = canonicalSelectionService;
        this.config = config;
    }

    /**
     * Clusters articles and selects canonical article for each cluster.
     * Returns clusters sorted by time (most recent first).
     */
    public List<ArticleCluster> deduplicateArticles(List<Article> articles) {
        // Cluster articles
        List<ArticleCluster> clusters = clusteringService.cluster(articles, config);

        // Select canonical for each cluster
        for (ArticleCluster cluster : clusters) {
            Article canonical = canonicalSelectionService.selectCanonical(cluster);
            cluster.setCanonical(canonical);
        }

        // Sort clusters by canonical article time (most recent first)
        clusters.sort(Comparator.comparing(
            (ArticleCluster c) -> c.getCanonical().getEffectiveDate()
        ).reversed());

        return clusters;
    }
}
