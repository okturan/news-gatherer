package com.newsgatherer.infrastructure.similarity;

import com.newsgatherer.domain.model.Article;
import com.newsgatherer.domain.service.ArticleSimilarityService;

import java.util.Set;

/**
 * Implements similarity calculation using Jaccard index on shingles.
 */
public class JaccardSimilarityService implements ArticleSimilarityService {

    @Override
    public double calculateSimilarity(Article article1, Article article2) {
        Set<String> shingles1 = article1.shingles();
        Set<String> shingles2 = article2.shingles();

        if (shingles1 == null || shingles2 == null) {
            return 0.0;
        }

        if (shingles1.isEmpty() || shingles2.isEmpty()) {
            return 0.0;
        }

        return jaccardIndex(shingles1, shingles2);
    }

    @Override
    public boolean areSimilar(Article article1, Article article2, double threshold) {
        return calculateSimilarity(article1, article2) >= threshold;
    }

    /**
     * Calculates Jaccard index between two sets.
     * Jaccard index = |A ∩ B| / |A ∪ B|
     */
    private double jaccardIndex(Set<String> set1, Set<String> set2) {
        int intersectionSize = 0;
        for (String element : set1) {
            if (set2.contains(element)) {
                intersectionSize++;
            }
        }

        int unionSize = set1.size() + set2.size() - intersectionSize;

        return unionSize == 0 ? 0.0 : (double) intersectionSize / unionSize;
    }
}
