package com.newsgatherer.domain.repository;

/**
 * Exception thrown when article repository operations fail.
 */
public class ArticleRepositoryException extends Exception {

    public ArticleRepositoryException(String message) {
        super(message);
    }

    public ArticleRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
