package com.newsgatherer.application.service;

import com.newsgatherer.config.GdeltApiConfig;
import com.newsgatherer.domain.model.Article;
import com.newsgatherer.domain.repository.ArticleRepository;
import com.newsgatherer.domain.repository.ArticleRepositoryException;

import java.util.List;

/**
 * Application service for gathering articles from external sources.
 */
public class ArticleGatheringService {
    private final ArticleRepository repository;
    private final GdeltApiConfig config;

    public ArticleGatheringService(ArticleRepository repository, GdeltApiConfig config) {
        this.repository = repository;
        this.config = config;
    }

    /**
     * Gathers articles using default configuration.
     */
    public List<Article> gatherArticles(String query) throws ArticleRepositoryException {
        return gatherArticles(
            query,
            formatTimespan(config.getDefaultTimespan()),
            config.getMaxRecords()
        );
    }

    /**
     * Gathers articles with specific parameters.
     */
    public List<Article> gatherArticles(String query, String timespan, int maxRecords)
            throws ArticleRepositoryException {
        return repository.findArticles(query, timespan, maxRecords);
    }

    /**
     * Formats Duration to GDELT timespan format (e.g., "2h", "7d").
     */
    private String formatTimespan(java.time.Duration duration) {
        long hours = duration.toHours();
        if (hours < 24) {
            return hours + "h";
        }
        return (hours / 24) + "d";
    }
}
