package com.newsgatherer.domain.service;

import com.newsgatherer.domain.model.Article;

/**
 * Domain service for calculating similarity between articles.
 */
public interface ArticleSimilarityService {

    /**
     * Calculates the similarity score between two articles.
     *
     * @param article1 first article
     * @param article2 second article
     * @return similarity score between 0.0 (completely different) and 1.0 (identical)
     */
    double calculateSimilarity(Article article1, Article article2);

    /**
     * Determines if two articles are similar enough based on a threshold.
     *
     * @param article1 first article
     * @param article2 second article
     * @param threshold minimum similarity threshold (0.0 to 1.0)
     * @return true if articles are similar enough
     */
    boolean areSimilar(Article article1, Article article2, double threshold);
}
