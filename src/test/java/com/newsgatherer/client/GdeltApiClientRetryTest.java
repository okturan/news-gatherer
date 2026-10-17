package com.newsgatherer.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsgatherer.client.GdeltApiClient.FetchResult;
import com.newsgatherer.client.GdeltApiClient.GdeltApiException;
import com.newsgatherer.client.GdeltApiClient.RetryPolicy;
import com.newsgatherer.parser.ArticleParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Drives the client against a local HTTP server; no real network and no real sleeping. */
class GdeltApiClientRetryTest {

    private record Reply(int status, String body, String retryAfter) {
    }

    private static final String ONE_ARTICLE =
        "{\"articles\":[{\"url\":\"https://example.com/a\",\"title\":\"A\",\"domain\":\"example.com\"}]}";

    private HttpServer server;
    private final List<Reply> script = new ArrayList<>();
    private final AtomicInteger hits = new AtomicInteger();
    private final List<Long> sleeps = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/doc", exchange -> {
            int index = Math.min(hits.getAndIncrement(), script.size() - 1);
            Reply reply = script.get(index);
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            if (reply.retryAfter() != null) {
                exchange.getResponseHeaders().add("Retry-After", reply.retryAfter());
            }
            exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private GdeltApiClient client(int maxAttempts) {
        RetryPolicy policy = new RetryPolicy(maxAttempts, Duration.ofSeconds(5), Duration.ofSeconds(120), () -> 0.0);
        return new GdeltApiClient(
            HttpClient.newHttpClient(),
            new ObjectMapper(),
            new ArticleParser(),
            "http://127.0.0.1:" + server.getAddress().getPort() + "/doc",
            Duration.ZERO,
            policy,
            sleeps::add
        );
    }

    private static Reply reply(int status, String body) {
        return new Reply(status, body, null);
    }

    @Test
    void retriesAfter429ThenSucceeds() throws Exception {
        script.add(reply(429, "Please limit requests to one every 5 seconds."));
        script.add(reply(200, ONE_ARTICLE));

        FetchResult result = client(4).fetchArticlesWithMetadata("deprem", "24h", 200);

        assertThat(result.articles()).hasSize(1);
        assertThat(result.emptyResponse()).isFalse();
        assertThat(hits.get()).isEqualTo(2);
        assertThat(sleeps).containsExactly(5_000L);
    }

    @Test
    void backsOffExponentiallyOn5xx() throws Exception {
        script.add(reply(503, ""));
        script.add(reply(502, ""));
        script.add(reply(200, ONE_ARTICLE));

        client(4).fetchArticlesWithMetadata("deprem", "24h", 200);

        assertThat(sleeps).containsExactly(5_000L, 10_000L);
    }

    @Test
    void failsClearlyWhenAttemptsAreExhausted() {
        script.add(reply(429, "slow down"));

        assertThatThrownBy(() -> client(3).fetchArticlesWithMetadata("deprem", "24h", 200))
            .isInstanceOfSatisfying(GdeltApiException.class, e -> {
                assertThat(e.status()).isEqualTo(429);
                assertThat(e.getMessage())
                    .contains("status 429", "after 3 attempt(s)", "waited 15s", "one request every 5 seconds");
            });
        assertThat(hits.get()).isEqualTo(3);
        assertThat(sleeps).hasSize(2);
    }

    @Test
    void doesNotRetryOtherClientErrors() {
        script.add(reply(400, "bad"));

        assertThatThrownBy(() -> client(4).fetchArticlesWithMetadata("deprem", "24h", 200))
            .isInstanceOf(GdeltApiException.class)
            .hasMessageContaining("status 400");
        assertThat(hits.get()).isEqualTo(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void honoursRetryAfterSeconds() throws Exception {
        script.add(new Reply(429, "", "17"));
        script.add(reply(200, ONE_ARTICLE));

        client(4).fetchArticlesWithMetadata("deprem", "24h", 200);

        assertThat(sleeps).containsExactly(17_000L);
    }

    @Test
    void capsAbsurdRetryAfterAtTheMaximumDelay() throws Exception {
        script.add(new Reply(429, "", "86400"));
        script.add(reply(200, ONE_ARTICLE));

        client(4).fetchArticlesWithMetadata("deprem", "24h", 200);

        assertThat(sleeps).containsExactly(120_000L);
    }

    @Test
    void parsesRetryAfterValues() {
        assertThat(GdeltApiClient.parseRetryAfter(" 7 ")).contains(Duration.ofSeconds(7));
        assertThat(GdeltApiClient.parseRetryAfter("soon")).isEqualTo(Optional.empty());
        assertThat(GdeltApiClient.parseRetryAfter("Wed, 21 Oct 2015 07:28:00 GMT")).contains(Duration.ZERO);
    }

    @Test
    void jitterAddsAtMostAQuarterOfTheDelay() {
        RetryPolicy policy = new RetryPolicy(4, Duration.ofSeconds(4), Duration.ofSeconds(120), () -> 1.0);
        assertThat(policy.delayFor(1, Optional.empty())).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.delayFor(2, Optional.empty())).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void emptyBodyIsFlaggedAsNothingReturned() throws Exception {
        script.add(reply(200, ""));

        FetchResult result = client(4).fetchArticlesWithMetadata("deprem", "24h", 200);

        assertThat(result.emptyResponse()).isTrue();
        assertThat(result.articles()).isEmpty();
    }

    @Test
    void emptyJsonObjectIsFlaggedAsNothingReturned() throws Exception {
        script.add(reply(200, "{}"));

        assertThat(client(4).fetchArticlesWithMetadata("deprem", "24h", 200).emptyResponse()).isTrue();
    }

    @Test
    void validZeroResultsIsNotFlagged() throws Exception {
        script.add(reply(200, "{\"articles\":[]}"));

        FetchResult result = client(4).fetchArticlesWithMetadata("deprem", "24h", 200);

        assertThat(result.emptyResponse()).isFalse();
        assertThat(result.articles()).isEmpty();
    }

    @Test
    void plainTextReplyOn200ReportsTheMessage() {
        script.add(reply(200, "Your query contained a phrase that is too short."));

        assertThatThrownBy(() -> client(4).fetchArticlesWithMetadata("a", "24h", 200))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("non-JSON reply")
            .hasMessageContaining("too short");
    }

    @Test
    void reportsReachingTheRecordCap() throws Exception {
        StringBuilder body = new StringBuilder("{\"articles\":[");
        for (int i = 0; i < 200; i++) {
            body.append(i == 0 ? "" : ",")
                .append("{\"url\":\"https://example.com/").append(i)
                .append("\",\"title\":\"T").append(i).append("\",\"domain\":\"example.com\"}");
        }
        script.add(reply(200, body.append("]}").toString()));

        FetchResult result = client(4).fetchArticlesWithMetadata("deprem", "24h", 200);

        assertThat(result.responseCount()).isEqualTo(200);
        assertThat(result.reachedLimit(200)).isTrue();
    }

    @Test
    void spacesRequestsByTheConfiguredInterval() throws Exception {
        script.add(reply(200, ONE_ARTICLE));
        GdeltApiClient spaced = new GdeltApiClient(
            HttpClient.newHttpClient(), new ObjectMapper(), new ArticleParser(),
            "http://127.0.0.1:" + server.getAddress().getPort() + "/doc",
            Duration.ofSeconds(5),
            new RetryPolicy(2, Duration.ofSeconds(5), Duration.ofSeconds(120), () -> 0.0),
            sleeps::add);

        spaced.fetchArticlesWithMetadata("deprem", "24h", 200);
        spaced.fetchArticlesWithMetadata("deprem", "24h", 200);

        assertThat(sleeps).hasSize(1);
        assertThat(sleeps.get(0)).isBetween(1L, 5_000L);
    }
}
