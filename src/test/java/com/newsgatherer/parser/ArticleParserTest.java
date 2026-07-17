package com.newsgatherer.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.newsgatherer.domain.Article;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ArticleParser parser = new ArticleParser();

    @Test
    void extractsBothSupportedArticleArrayShapes() throws Exception {
        JsonNode articles = objectMapper.readTree("""
            {"articles": [{"url": "https://example.com/a", "title": "A"}]}
            """);
        JsonNode artlist = objectMapper.readTree("""
            {"artlist": [{"url": "https://example.com/b", "title": "B"}]}
            """);

        assertThat(parser.extractArticlesArray(articles)).hasSize(1);
        assertThat(parser.extractArticlesArray(artlist)).hasSize(1);
        assertThat(parser.extractArticlesArray(objectMapper.readTree("{}"))).isNull();
        assertThat(parser.extractArticlesArray(null)).isNull();
    }

    @Test
    void canonicalizesUrlsAndParsesCompactGdeltDates() throws Exception {
        ArrayNode array = (ArrayNode) objectMapper.readTree("""
            [{
              "url": "https://www.example.com/news/?utm_source=feed&fbclid=tracking&keep=1#fragment",
              "title": "Canonical story",
              "seendate": "20250102T030405Z"
            }]
            """);

        List<Article> parsed = parser.parseArticles(array);

        assertThat(parsed).singleElement().satisfies(article -> {
            assertThat(article.domain()).isEqualTo("example.com");
            assertThat(article.canonicalUrl()).isEqualTo("https://www.example.com/news?keep=1");
            assertThat(article.seenDate().toInstant()).isEqualTo(Instant.parse("2025-01-02T03:04:05Z"));
            assertThat(article.seenDate().getZone()).isEqualTo(ZoneId.of("Europe/Istanbul"));
        });
    }

    @Test
    void filtersRecordsWithoutRequiredFields() throws Exception {
        ArrayNode array = (ArrayNode) objectMapper.readTree("""
            [
              {"url": "https://example.com/missing-title"},
              {"title": "Missing URL"},
              {"url": "https://example.com/valid", "title": "Valid"}
            ]
            """);

        assertThat(parser.parseArticles(array))
            .extracting(Article::title)
            .containsExactly("Valid");
        assertThat(parser.parseArticles(null)).isEmpty();
    }
}
