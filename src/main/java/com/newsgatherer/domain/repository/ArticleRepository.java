package com.newsgatherer.domain.repository;

import com.newsgatherer.domain.model.Article;

import java.util.List;

/**
 * Repository interface for article data access.
 * Abstracts the source of articles (API, database, cache, etc.).
 */
public interface ArticleRepository {

    /**
     * Fetches articles matching the given query.
     *
     * @param query search query parameters
     * @param timespan time range (e.g., "2h", "7d")
     * @param maxRecords maximum number of records to fetch
     * @return list of matching articles
     * @throws ArticleRepositoryException if fetching fails
     */
    List<Article> findArticles(String query, String timespan, int maxRecords)
        throws ArticleRepositoryException;
}
