package com.newsgatherer.domain.service;

import com.newsgatherer.domain.model.Article;
import com.newsgatherer.domain.model.ArticleCluster;

/**
 * Domain service for selecting the canonical (representative) article from a cluster.
 */
public interface CanonicalSelectionService {

    /**
     * Selects the canonical article from a cluster.
     * The canonical article represents the entire cluster and should be the most authoritative.
     *
     * @param cluster the cluster to select from
     * @return the canonical article
     */
    Article selectCanonical(ArticleCluster cluster);
}
