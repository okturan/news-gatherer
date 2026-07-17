package com.newsgatherer.storage;

import com.newsgatherer.domain.Article;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArticleRepositoryTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void persistsFieldsAndDeduplicatesAcrossRepositoryReopens() throws Exception {
        Path database = temporaryDirectory.resolve("nested/news.db");
        Article article = article("https://example.com/story?utm_source=feed", "https://example.com/story");

        try (ArticleRepository repository = new ArticleRepository(database.toString())) {
            Map<String, Long> seen = repository.loadSeenUrls();

            assertThat(repository.saveNewArticles(List.of(article, article), seen))
                .containsExactly(article);
            assertThat(seen).containsKey(article.canonicalUrl());
        }

        try (ArticleRepository repository = new ArticleRepository(database.toString())) {
            Map<String, Long> seen = repository.loadSeenUrls();
            assertThat(repository.saveNewArticles(List.of(article), seen)).isEmpty();
        }

        try (Connection connection = connect(database);
             Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT * FROM articles")) {
            assertThat(row.next()).isTrue();
            assertThat(row.getString("url")).isEqualTo(article.url());
            assertThat(row.getString("title")).isEqualTo(article.title());
            assertThat(row.getString("published_date")).isEqualTo(article.publishedDate().toString());
            assertThat(row.getString("canonical_url")).isEqualTo(article.canonicalUrl());
            assertThat(row.next()).isFalse();
        }
    }

    @Test
    void rollsBackSeenKeyWhenArticleInsertFailsSoTheStoryCanBeRetried() throws Exception {
        Path database = temporaryDirectory.resolve("rollback.db");
        Article article = article("https://example.com/retry", "https://example.com/retry");
        Map<String, Long> seen = new HashMap<>();

        try (ArticleRepository repository = new ArticleRepository(database.toString())) {
            createFailingArticleTrigger(database);

            assertThatThrownBy(() -> repository.saveNewArticles(List.of(article), seen))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("forced article failure");
            assertThat(seen).doesNotContainKey(article.canonicalUrl());
            assertThat(repository.loadSeenUrls()).doesNotContainKey(article.canonicalUrl());
        }

        dropFailingArticleTrigger(database);
        try (ArticleRepository repository = new ArticleRepository(database.toString())) {
            assertThat(repository.saveNewArticles(List.of(article), repository.loadSeenUrls()))
                .containsExactly(article);
        }
    }

    private Article article(String url, String canonicalUrl) {
        return new Article(
            url,
            "Persistent story",
            "example.com",
            "Turkish",
            "Turkey",
            ZonedDateTime.of(2026, 7, 17, 9, 15, 0, 0, ZoneOffset.UTC),
            ZonedDateTime.of(2026, 7, 17, 9, 0, 0, 0, ZoneOffset.UTC),
            canonicalUrl
        );
    }

    private void createFailingArticleTrigger(Path database) throws SQLException {
        try (Connection connection = connect(database); Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TRIGGER fail_article_insert BEFORE INSERT ON articles
                BEGIN SELECT RAISE(ABORT, 'forced article failure'); END
                """);
        }
    }

    private void dropFailingArticleTrigger(Path database) throws SQLException {
        try (Connection connection = connect(database); Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER fail_article_insert");
        }
    }

    private Connection connect(Path database) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + database);
    }
}
