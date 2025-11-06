package com.newsgatherer.application.usecase;

import com.newsgatherer.application.dto.ClusteringResultDto;
import com.newsgatherer.application.service.ArticleDeduplicationService;
import com.newsgatherer.application.service.ArticleGatheringService;
import com.newsgatherer.domain.model.Article;
import com.newsgatherer.domain.model.ArticleCluster;
import com.newsgatherer.domain.repository.ArticleRepositoryException;

import java.util.List;

/**
 * Main use case for gathering and deduplicating news articles.
 */
public class GatherAndDeduplicateArticlesUseCase {
    private final ArticleGatheringService gatheringService;
    private final ArticleDeduplicationService deduplicationService;

    public GatherAndDeduplicateArticlesUseCase(
            ArticleGatheringService gatheringService,
            ArticleDeduplicationService deduplicationService) {
        this.gatheringService = gatheringService;
        this.deduplicationService = deduplicationService;
    }

    /**
     * Executes the complete workflow: gather articles, then cluster and deduplicate.
     *
     * @param query search query for articles
     * @return clustering results with metrics
     * @throws ArticleRepositoryException if article gathering fails
     */
    public ClusteringResultDto execute(String query) throws ArticleRepositoryException {
        // Gather articles
        List<Article> articles = gatheringService.gatherArticles(query);

        if (articles.isEmpty()) {
            return ClusteringResultDto.empty();
        }

        // Deduplicate and cluster
        List<ArticleCluster> clusters = deduplicationService.deduplicateArticles(articles);

        // Build result DTO
        return ClusteringResultDto.from(articles, clusters);
    }
}
