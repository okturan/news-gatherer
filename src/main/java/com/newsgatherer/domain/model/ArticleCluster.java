package com.newsgatherer.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a cluster of similar news articles.
 */
public class ArticleCluster {
    private final List<Article> articles;
    private Article canonical;

    public ArticleCluster() {
        this.articles = new ArrayList<>();
    }

    public ArticleCluster(List<Article> articles) {
        this.articles = new ArrayList<>(articles);
    }

    /**
     * Adds an article to this cluster.
     */
    public void add(Article article) {
        articles.add(article);
    }

    /**
     * Sets the canonical (representative) article for this cluster.
     */
    public void setCanonical(Article canonical) {
        if (!articles.contains(canonical)) {
            throw new IllegalArgumentException("Canonical article must be in the cluster");
        }
        this.canonical = canonical;
    }

    /**
     * Returns the canonical article, or the first article if not set.
     */
    public Article getCanonical() {
        if (canonical != null) {
            return canonical;
        }
        if (!articles.isEmpty()) {
            return articles.getFirst();  // Java 21 sequenced collections
        }
        return null;
    }

    /**
     * Returns all articles in this cluster.
     */
    public List<Article> getArticles() {
        return Collections.unmodifiableList(articles);
    }

    /**
     * Returns the number of articles in this cluster.
     */
    public int size() {
        return articles.size();
    }

    /**
     * Returns true if this cluster is empty.
     */
    public boolean isEmpty() {
        return articles.isEmpty();
    }
}
