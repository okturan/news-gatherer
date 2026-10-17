package com.newsgatherer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsgatherer.client.GdeltApiClient;
import com.newsgatherer.config.CliOptions;
import com.newsgatherer.output.ArticlePrinter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end CLI flow against a local server: exit codes and user-facing messages. */
class GdeltNewsGathererRunTest {

    @TempDir
    Path tempDir;

    private HttpServer server;
    private volatile String body = "";
    private final PrintStream originalOut = System.out;
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/doc", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        server.stop(0);
    }

    private GdeltNewsGatherer gatherer() {
        GdeltApiClient client = new GdeltApiClient(
            HttpClient.newHttpClient(), new ObjectMapper(), new com.newsgatherer.parser.ArticleParser(),
            "http://127.0.0.1:" + server.getAddress().getPort() + "/doc",
            Duration.ZERO,
            new GdeltApiClient.RetryPolicy(1, Duration.ofSeconds(1), Duration.ofSeconds(1), () -> 0.0),
            millis -> { });
        return new GdeltNewsGatherer(client, new ArticlePrinter(System.out), new ObjectMapper(),
            tempDir.resolve("test.db").toString());
    }

    private String output() {
        return captured.toString(StandardCharsets.UTF_8);
    }

    private static String articles(int count) {
        StringBuilder sb = new StringBuilder("{\"articles\":[");
        for (int i = 0; i < count; i++) {
            sb.append(i == 0 ? "" : ",").append("{\"url\":\"https://example.com/").append(i)
                .append("\",\"title\":\"T").append(i).append("\",\"domain\":\"example.com\"}");
        }
        return sb.append("]}").toString();
    }

    @Test
    void emptyReplyWarnsAndExitsWithDistinctCode() throws Exception {
        body = "{}";

        int code = gatherer().run(CliOptions.parse(new String[] {"--query=deprem", "--timespan=24h"}));

        assertThat(code).isEqualTo(GdeltNewsGatherer.EXIT_EMPTY_RESPONSE).isNotEqualTo(GdeltNewsGatherer.EXIT_OK);
        assertThat(output()).contains("GDELT returned nothing: the query may be invalid or throttled")
            .doesNotContain("No articles found");
    }

    @Test
    void validZeroResultsStaysExitZero() throws Exception {
        body = "{\"articles\":[]}";

        int code = gatherer().run(CliOptions.parse(new String[] {"--query=deprem", "--timespan=24h"}));

        assertThat(code).isEqualTo(GdeltNewsGatherer.EXIT_OK);
        assertThat(output()).contains("No articles found.").doesNotContain("returned nothing");
    }

    @Test
    void capHitSuggestsBackfill() throws Exception {
        body = articles(200);

        int code = gatherer().run(CliOptions.parse(new String[] {"--query=deprem", "--timespan=24h"}));

        assertThat(code).isEqualTo(GdeltNewsGatherer.EXIT_OK);
        assertThat(output()).contains("200-record cap", "truncated", "--lookback=24h");
    }

    @Test
    void belowCapPrintsNoTruncationNote() throws Exception {
        body = articles(5);

        gatherer().run(CliOptions.parse(new String[] {"--query=deprem", "--timespan=24h"}));

        assertThat(output()).doesNotContain("truncated");
    }

    @Test
    void secondRunReportsEverythingAsAlreadySeen() throws Exception {
        body = articles(5);
        CliOptions options = CliOptions.parse(new String[] {"--query=deprem", "--timespan=24h"});

        gatherer().run(options);
        captured.reset();
        gatherer().run(options);

        assertThat(output()).contains("Tracking 5 previously seen URLs", "No new articles (all previously seen).");
    }

    @Test
    void backfillWithEmptyWindowsExitsWithWarningCode() throws Exception {
        body = "";

        int code = gatherer().runBackfill(CliOptions.parse(new String[] {"--lookback=2h", "--window=1h"}));

        assertThat(code).isEqualTo(GdeltNewsGatherer.EXIT_EMPTY_RESPONSE);
        assertThat(output()).contains("GDELT returned nothing", "2 window(s) came back empty");
    }
}
