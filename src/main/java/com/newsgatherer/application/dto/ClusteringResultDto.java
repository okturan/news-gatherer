package com.newsgatherer.application.dto;

import com.newsgatherer.domain.model.Article;
import com.newsgatherer.domain.model.ArticleCluster;

import java.util.List;

/**
 * Data Transfer Object for clustering results.
 */
public record ClusteringResultDto(
    List<ArticleCluster> clusters,
    int totalArticles,
    int totalClusters,
    double averageClusterSize
) {

    public static ClusteringResultDto from(List<Article> articles, List<ArticleCluster> clusters) {
        int totalArticles = articles.size();
        int totalClusters = clusters.size();
        double averageClusterSize = totalClusters > 0
            ? (double) totalArticles / totalClusters
            : 0.0;

        return new ClusteringResultDto(
            clusters,
            totalArticles,
            totalClusters,
            averageClusterSize
        );
    }

    public static ClusteringResultDto empty() {
        return new ClusteringResultDto(List.of(), 0, 0, 0.0);
    }

    public boolean isEmpty() {
        return totalArticles == 0;
    }
}
