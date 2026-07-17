package com.newsgatherer.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.newsgatherer.parser.ArticleParser;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

class GdeltApiClientTest {

    @Test
    void warnsWhenAResponseFillsTheConfiguredRequestLimit() {
        assertThat(GdeltApiClient.requestLimitWarning(200, 200))
            .isEqualTo("  → Hit 200 articles (request limit), results may be truncated");
    }

    @Test
    void doesNotWarnBelowTheConfiguredRequestLimit() {
        assertThat(GdeltApiClient.requestLimitWarning(199, 200)).isNull();
    }

    @Test
    void doesNotWarnForAnInvalidRequestLimit() {
        assertThat(GdeltApiClient.requestLimitWarning(1, 0)).isNull();
    }

    @Test
    void preservesRawResponseCountWhenTheParserDropsInvalidRows() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode payload = (ArrayNode) objectMapper.readTree("""
            [
              {"url": "https://example.com/valid", "title": "Valid"},
              {"url": "https://example.com/missing-title"}
            ]
            """);
        GdeltApiClient client = new GdeltApiClient(
            HttpClient.newHttpClient(),
            objectMapper,
            new ArticleParser()
        );

        GdeltApiClient.FetchResult result = client.toFetchResult(payload);

        assertThat(result.responseCount()).isEqualTo(2);
        assertThat(result.articles()).singleElement()
            .extracting(article -> article.title())
            .isEqualTo("Valid");
    }
}
