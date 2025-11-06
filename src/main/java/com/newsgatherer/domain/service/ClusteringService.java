package com.newsgatherer.domain.service;

import com.newsgatherer.config.ClusteringConfig;
import com.newsgatherer.domain.model.Article;
import com.newsgatherer.domain.model.ArticleCluster;

import java.util.List;

/**
 * Domain service for clustering articles by similarity.
 */
public interface ClusteringService {

    /**
     * Clusters articles based on similarity within a time window.
     *
     * @param articles the articles to cluster
     * @param config clustering configuration (time window, threshold, etc.)
     * @return list of article clusters
     */
    List<ArticleCluster> cluster(List<Article> articles, ClusteringConfig config);
}
