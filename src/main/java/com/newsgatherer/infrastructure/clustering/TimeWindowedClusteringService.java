package com.newsgatherer.infrastructure.clustering;

import com.newsgatherer.config.ClusteringConfig;
import com.newsgatherer.domain.model.Article;
import com.newsgatherer.domain.model.ArticleCluster;
import com.newsgatherer.domain.service.ArticleSimilarityService;
import com.newsgatherer.domain.service.ClusteringService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Implements clustering using time-windowed similarity comparison.
 * Uses Union-Find for efficient grouping of similar articles.
 */
public class TimeWindowedClusteringService implements ClusteringService {
    private final ArticleSimilarityService similarityService;

    public TimeWindowedClusteringService(ArticleSimilarityService similarityService) {
        this.similarityService = similarityService;
    }

    @Override
    public List<ArticleCluster> cluster(List<Article> articles, ClusteringConfig config) {
        if (articles == null || articles.isEmpty()) {
            return List.of();
        }

        // Sort by time (most recent first)
        List<Integer> sortedIndices = createTimeSortedIndices(articles);

        // Initialize Union-Find
        UnionFind unionFind = new UnionFind(articles.size());

        // Group similar articles within time window
        groupSimilarArticles(
            articles,
            sortedIndices,
            unionFind,
            config.getTimeWindow(),
            config.getSimilarityThreshold()
        );

        // Build clusters from Union-Find groups
        return buildClusters(articles, unionFind);
    }

    /**
     * Creates a list of indices sorted by article timestamp (most recent first).
     */
    private List<Integer> createTimeSortedIndices(List<Article> articles) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < articles.size(); i++) {
            indices.add(i);
        }

        indices.sort(Comparator.comparing(
            (Integer i) -> articles.get(i).getEffectiveDate()
        ).reversed());

        return indices;
    }

    /**
     * Groups articles that are similar and within the time window.
     */
    private void groupSimilarArticles(
            List<Article> articles,
            List<Integer> sortedIndices,
            UnionFind unionFind,
            Duration timeWindow,
            double threshold) {

        for (int i = 0; i < sortedIndices.size(); i++) {
            int indexA = sortedIndices.get(i);
            Article articleA = articles.get(indexA);

            // Compare with subsequent articles in time order
            for (int j = i + 1; j < sortedIndices.size(); j++) {
                int indexB = sortedIndices.get(j);
                Article articleB = articles.get(indexB);

                // Stop if outside time window
                Duration timeDiff = Duration.between(
                    articleB.getEffectiveDate(),
                    articleA.getEffectiveDate()
                );

                if (timeDiff.compareTo(timeWindow) > 0) {
                    break;
                }

                // Group if similar enough
                if (similarityService.areSimilar(articleA, articleB, threshold)) {
                    unionFind.union(indexA, indexB);
                }
            }
        }
    }

    /**
     * Builds ArticleCluster objects from Union-Find groups.
     */
    private List<ArticleCluster> buildClusters(List<Article> articles, UnionFind unionFind) {
        Map<Integer, List<Integer>> groups = unionFind.getAllSets();

        List<ArticleCluster> clusters = new ArrayList<>();
        for (List<Integer> group : groups.values()) {
            ArticleCluster cluster = new ArticleCluster();
            for (int index : group) {
                cluster.add(articles.get(index));
            }
            clusters.add(cluster);
        }

        return clusters;
    }
}
