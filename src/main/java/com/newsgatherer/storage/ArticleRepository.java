package com.newsgatherer.storage;

import com.newsgatherer.config.Config;
import com.newsgatherer.domain.Article;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages persistence of articles and seen URLs in SQLite.
 */
public class ArticleRepository implements AutoCloseable {

    private static final String CREATE_SEEN_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS seen_urls (
            canonical_url TEXT PRIMARY KEY,
            first_seen INTEGER NOT NULL
        )
        """;

    private static final String CREATE_ARTICLES_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS articles (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            url TEXT NOT NULL,
            title TEXT NOT NULL,
            domain TEXT NOT NULL,
            language TEXT,
            source_country TEXT,
            seen_date TEXT,
            published_date TEXT,
            canonical_url TEXT NOT NULL,
            stored_at INTEGER NOT NULL
        )
        """;

    private final Connection connection;

    public ArticleRepository(String databaseFile) throws SQLException, IOException {
        Path dbPath = Path.of(databaseFile).toAbsolutePath();
        Path parent = dbPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
        createTables();
        pruneSeenUrls();
    }

    public Map<String, Long> loadSeenUrls() throws SQLException {
        Map<String, Long> seen = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                 "SELECT canonical_url, first_seen FROM seen_urls")) {
            while (resultSet.next()) {
                seen.put(resultSet.getString(1), resultSet.getLong(2));
            }
        }
        return seen;
    }

    public List<Article> filterNewArticles(List<Article> articles,
                                           Map<String, Long> seenUrls) throws SQLException {
        long now = System.currentTimeMillis();
        List<Article> newArticles = new ArrayList<>();

        String insertSql = "INSERT OR IGNORE INTO seen_urls(canonical_url, first_seen) VALUES (?, ?)";
        try (PreparedStatement insertSeen = connection.prepareStatement(insertSql)) {
            for (Article article : articles) {
                String canonical = article.canonicalUrl();
                if (!seenUrls.containsKey(canonical)) {
                    newArticles.add(article);
                    seenUrls.put(canonical, now);
                    insertSeen.setString(1, canonical);
                    insertSeen.setLong(2, now);
                    insertSeen.executeUpdate();
                }
            }
        }

        if (articles.size() != newArticles.size()) {
            System.out.println("  → Filtered " + (articles.size() - newArticles.size())
                + " previously seen articles");
        }

        return newArticles;
    }

    public void saveArticles(List<Article> articles) throws SQLException {
        if (articles.isEmpty()) {
            return;
        }

        String sql = """
            INSERT INTO articles (
                url, title, domain, language, source_country,
                seen_date, published_date, canonical_url, stored_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        long storedAt = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Article article : articles) {
                statement.setString(1, article.url());
                statement.setString(2, article.title());
                statement.setString(3, article.domain());
                statement.setString(4, article.language());
                statement.setString(5, article.sourceCountry());
                statement.setString(6, formatDate(article.seenDate()));
                statement.setString(7, formatDate(article.publishedDate()));
                statement.setString(8, article.canonicalUrl());
                statement.setLong(9, storedAt);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_SEEN_TABLE_SQL);
            statement.execute(CREATE_ARTICLES_TABLE_SQL);
        }

        migrateLegacySourceTypeColumn();
    }

    private void migrateLegacySourceTypeColumn() throws SQLException {
        if (!tableHasColumn("articles", "source_type")) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE articles RENAME TO articles_old");
            statement.execute(CREATE_ARTICLES_TABLE_SQL);
            statement.execute("""
                INSERT INTO articles (
                    url, title, domain, language, source_country,
                    seen_date, published_date, canonical_url, stored_at)
                SELECT url, title, domain, language, source_country,
                       seen_date, published_date, canonical_url, stored_at
                FROM articles_old
                """);
            statement.execute("DROP TABLE articles_old");
        }
    }

    private boolean tableHasColumn(String tableName, String columnName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void pruneSeenUrls() throws SQLException {
        long cutoff = System.currentTimeMillis() - Config.SEEN_URL_RETENTION.toMillis();
        try (PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM seen_urls WHERE first_seen < ?")) {
            statement.setLong(1, cutoff);
            statement.executeUpdate();
        }
    }

    private String formatDate(ZonedDateTime date) {
        return date != null ? date.toString() : null;
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
