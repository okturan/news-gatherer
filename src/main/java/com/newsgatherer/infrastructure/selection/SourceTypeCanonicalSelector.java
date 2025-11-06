package com.newsgatherer.infrastructure.selection;

import com.newsgatherer.domain.model.Article;
import com.newsgatherer.domain.model.ArticleCluster;
import com.newsgatherer.domain.model.SourceType;
import com.newsgatherer.domain.service.CanonicalSelectionService;

import java.util.Comparator;

/**
 * Selects canonical article based on source type priority and publication time.
 * Priority: WIRE > PUBLISHER > AGGREGATOR
 * Within same priority, selects earliest article.
 */
public class SourceTypeCanonicalSelector implements CanonicalSelectionService {

    @Override
    public Article selectCanonical(ArticleCluster cluster) {
        if (cluster == null || cluster.isEmpty()) {
            throw new IllegalArgumentException("Cluster cannot be null or empty");
        }

        // Use Java 21 sequenced collections and modern comparator
        return cluster.getArticles().stream()
            .min(Comparator
                .comparing(Article::sourceType, SourceType.byPriority())
                .thenComparing(Article::getEffectiveDate))
            .orElseThrow(() -> new IllegalStateException("Empty cluster"));
    }
}
