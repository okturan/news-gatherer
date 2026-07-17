package com.newsgatherer;

import com.newsgatherer.client.GdeltApiClient.FetchResult;
import com.newsgatherer.domain.Article;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GdeltNewsGathererTest {

    @Test
    void splitsSaturatedWindowEvenWhenSomeRawRowsWereDiscarded() {
        Article parsedArticle = new Article(
            "https://example.com/valid",
            "Valid",
            "example.com",
            "English",
            "United States",
            null,
            null,
            "https://example.com/valid"
        );
        FetchResult saturated = new FetchResult(List.of(parsedArticle), 200);

        assertThat(GdeltNewsGatherer.shouldSplitWindow(saturated, Duration.ofHours(1))).isTrue();
        assertThat(GdeltNewsGatherer.shouldSplitWindow(saturated, Duration.ofMinutes(30))).isFalse();
    }
}
