package com.newsgatherer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Main application for fetching, clustering, and displaying Turkish news articles from GDELT.
 *
 * Simplified architecture - all logic in one place for clarity and maintainability.
 */
public class GdeltNewsClustering {

    private static final ZoneId ISTANBUL_TZ = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter GDELT_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SHORT_DATE_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private long lastRequestTime = 0;

    public GdeltNewsClustering() {
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Config.HTTP_TIMEOUT)
            .build();

        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    }

    public static void main(String[] args) {
        try {
            GdeltNewsClustering app = new GdeltNewsClustering();
            app.run(Config.DEFAULT_QUERY, Config.DEFAULT_TIMESPAN);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    public void run(String query, String timespan) throws Exception {
        // Fetch articles
        System.out.println("Fetching articles from GDELT...");
        List<Article> articles = fetchArticles(query, timespan, Config.MAX_ARTICLES);

        if (articles.isEmpty()) {
            System.out.println("No articles found.");
            return;
        }

        System.out.println("Fetched " + articles.size() + " articles.");

        // Cluster similar articles
        System.out.println("Clustering similar articles...");
        List<List<Article>> clusters = clusterArticles(articles);

        System.out.println("Found " + clusters.size() + " unique stories.");
        System.out.println();

        // Display results
        displayClusters(clusters);
        displayMetrics(articles.size(), clusters);
    }

    // ============================================================================
    // ARTICLE FETCHING AND PARSING
    // ============================================================================

    private List<Article> fetchArticles(String query, String timespan, int maxRecords) throws Exception {
        applyRateLimit();
        String queryString = buildQueryString(query, timespan, maxRecords);
        URI uri = new URI(Config.GDELT_API_ENDPOINT + "?" + queryString);
        HttpRequest request = HttpRequest.newBuilder(uri)
            .GET()
            .header("Accept", "application/json")
            .build();
        HttpResponse<InputStream> response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofInputStream()
        );
        if (response.statusCode() != 200) {
            throw new RuntimeException("GDELT API returned status " + response.statusCode());
        }
        try (InputStream body = response.body()) {
            JsonNode root = objectMapper.readTree(body);
            List<Article> articles = parseArticles(root);
            // Check if we hit the limit and need to split the time window
            if (articles.size() >= Config.SAFE_MAX_RECORDS) {
                System.out.println("  → Hit " + articles.size() + " articles (near limit), results may be truncated");
            }
            return articles;
        }
    }

    private void applyRateLimit() throws InterruptedException {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTime;
        long minInterval = Config.MIN_REQUEST_INTERVAL.toMillis();
        if (elapsed < minInterval && lastRequestTime > 0) {
            long sleepTime = minInterval - elapsed;
            Thread.sleep(sleepTime);
        }
        lastRequestTime = System.currentTimeMillis();
    }

    private String buildQueryString(String query, String timespan, int maxRecords) {
        return "query=" + URLEncoder.encode(query, StandardCharsets.UTF_8) +
               "&mode=artlist" +
               "&format=json" +
               "&timespan=" + URLEncoder.encode(timespan, StandardCharsets.UTF_8) +
               "&maxrecords=" + maxRecords +
               "&sort=datedesc";
    }

    private List<Article> parseArticles(JsonNode root) {
        ArrayNode articlesArray = extractArticlesArray(root);
        if (articlesArray == null) {
            return List.of();
        }

        return StreamSupport.stream(articlesArray.spliterator(), false)
            .map(this::parseArticle)
            .filter(Objects::nonNull)
            .toList();
    }

    private ArrayNode extractArticlesArray(JsonNode root) {
        if (root.has("articles") && root.get("articles").isArray()) {
            return (ArrayNode) root.get("articles");
        }
        if (root.has("artlist") && root.get("artlist").isArray()) {
            return (ArrayNode) root.get("artlist");
        }
        return null;
    }

    private Article parseArticle(JsonNode node) {
        try {
            String url = extractField(node, "url", "urlMobile", "link");
            String title = extractField(node, "title", "titleMobile");

            if (url == null || title == null) {
                return null;
            }

            // Input validation
            if (title.length() > Config.MAX_TITLE_LENGTH || url.length() > Config.MAX_URL_LENGTH) {
                return null;
            }

            String domain = extractDomain(node, url);
            String language = extractField(node, "language", "sourcelang");
            String sourceCountry = extractField(node, "sourcecountry");
            ZonedDateTime seenDate = parseDate(extractField(node, "seendate", "date"));
            ZonedDateTime publishDate = parseDate(extractField(node, "publishdate", "published"));

            SourceType sourceType = SourceType.fromDomain(domain);
            String normalizedTitle = normalizeText(title);
            Set<String> shingles = generateShingles(normalizedTitle);
            String canonicalUrl = canonicalizeUrl(url);

            return new Article(
                url, title, domain, language, sourceCountry,
                seenDate, publishDate, sourceType,
                normalizedTitle, shingles, canonicalUrl
            );
        } catch (Exception _) {
            return null; // Skip invalid articles
        }
    }

    private String extractField(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            if (node.has(field) && !node.get(field).isNull()) {
                return node.get(field).asText();
            }
        }
        return null;
    }

    private String extractDomain(JsonNode node, String url) {
        String domain = node.has("domain") && !node.get("domain").isNull()
            ? node.get("domain").asText()
            : extractDomainFromUrl(url);
        return domain.toLowerCase();
    }

    private String extractDomainFromUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) return "";
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception _) {
            return "";
        }
    }

    private ZonedDateTime parseDate(String dateString) {
        if (dateString == null) {
            return null;
        }

        try {
            if (dateString.length() == 19 && dateString.charAt(10) == ' ') {
                LocalDateTime ldt = LocalDateTime.parse(dateString, GDELT_DATE_FMT);
                return ldt.atZone(ISTANBUL_TZ);
            }
            return ZonedDateTime.parse(dateString).withZoneSameInstant(ISTANBUL_TZ);
        } catch (Exception _) {
            return null;
        }
    }

    // ============================================================================
    // TEXT PROCESSING
    // ============================================================================

    private String normalizeText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Turkish case normalization
        String normalized = text.replace("İ", "i")
                               .replace("I", "ı")
                               .toLowerCase(Config.TURKISH_LOCALE);

        // Remove punctuation and normalize whitespace
        normalized = Config.PUNCTUATION_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = Config.WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ");

        // Remove stop words
        String[] words = normalized.split(" ");
        StringBuilder result = new StringBuilder(normalized.length());

        for (String word : words) {
            if (!word.isEmpty() && !Config.STOP_WORDS.contains(word)) {
                if (result.length() > 0) result.append(' ');
                result.append(word);
            }
        }

        return result.toString().trim();
    }

    private Set<String> generateShingles(String text) {
        if (text == null || text.isEmpty()) {
            return Set.of();
        }

        String paddedText = " " + text + " ";
        Set<String> shingles = new HashSet<>();

        int maxIndex = Math.max(0, paddedText.length() - Config.SHINGLE_SIZE);
        for (int i = 0; i <= maxIndex; i++) {
            shingles.add(paddedText.substring(i, i + Config.SHINGLE_SIZE));
        }

        return shingles;
    }

    private String canonicalizeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        try {
            URI uri = new URI(url);

            // Strip trailing slash from path
            String path = uri.getPath();
            if (path != null && path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            // Filter tracking parameters from query
            String query = uri.getQuery();
            if (query != null && !query.isEmpty()) {
                query = Arrays.stream(query.split("&"))
                    .filter(param -> {
                        String key = param.split("=")[0].toLowerCase();
                        return !key.startsWith("utm_") && !Config.TRACKING_PARAMS.contains(key);
                    })
                    .collect(Collectors.joining("&"));
                query = query.isEmpty() ? null : query;
            }

            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(),
                          uri.getPort(), path, query, null).toString();

        } catch (Exception _) {
            return url;
        }
    }

    // ============================================================================
    // CLUSTERING
    // ============================================================================

    private List<List<Article>> clusterArticles(List<Article> articles) {
        int n = articles.size();
        UnionFind uf = new UnionFind(n);
        // Sort by time (newest first)
        List<Integer> indices = new ArrayList<>(n);
        for (int i = 0; i < n; i++) indices.add(i);
        indices.sort(Comparator.comparing((Integer i) -> articles.get(i).getEffectiveDate()).reversed());
        long timeWindowMillis = Config.TIME_WINDOW.toMillis();
        // Group similar articles within time window
        for (int i = 0; i < n; i++) {
            int idxA = indices.get(i);
            Article articleA = articles.get(idxA);
            long timeA = articleA.getEffectiveDate().toInstant().toEpochMilli();
            for (int j = i + 1; j < n; j++) {
                int idxB = indices.get(j);
                Article articleB = articles.get(idxB);
                long timeB = articleB.getEffectiveDate().toInstant().toEpochMilli();
                // Break if outside time window
                if (timeA - timeB > timeWindowMillis) {
                    break;
                }
                // Check similarity
                if (areSimilar(articleA, articleB)) {
                    uf.union(idxA, idxB);
                }
            }
        }
        // Build clusters
        Map<Integer, List<Article>> clusterMap = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int root = uf.find(i);
            clusterMap.computeIfAbsent(root, _ -> new ArrayList<>()).add(articles.get(i));
        }
        // Select canonical article for each cluster and sort
        List<List<Article>> clusters = clusterMap.values().stream()
            .map(this::sortClusterWithCanonical)
            .sorted(Comparator.comparing((List<Article> c) -> c.get(0).getEffectiveDate()).reversed())
            .toList();
        return clusters;
    }

    private List<Article> sortClusterWithCanonical(List<Article> cluster) {
        // Select canonical (highest priority source, then earliest time)
        Article canonical = cluster.stream()
            .min(Comparator.comparing((Article a) -> a.sourceType().getPriority())
                          .thenComparing(Article::getEffectiveDate))
            .orElse(cluster.get(0));

        // Sort cluster by time with canonical first
        List<Article> sorted = new ArrayList<>(cluster);
        sorted.sort(Comparator.comparing(Article::getEffectiveDate));

        // Move canonical to front
        if (!sorted.get(0).equals(canonical)) {
            sorted.remove(canonical);
            sorted.add(0, canonical);
        }

        return sorted;
    }

    private boolean areSimilar(Article a1, Article a2) {
        Set<String> s1 = a1.shingles();
        Set<String> s2 = a2.shingles();

        if (s1.isEmpty() || s2.isEmpty()) {
            return false;
        }

        // Calculate Jaccard similarity (iterate smaller set for performance)
        Set<String> smaller = s1.size() <= s2.size() ? s1 : s2;
        Set<String> larger = s1.size() <= s2.size() ? s2 : s1;

        int intersection = 0;
        for (String shingle : smaller) {
            if (larger.contains(shingle)) {
                intersection++;
            }
        }

        int union = s1.size() + s2.size() - intersection;
        double similarity = union == 0 ? 0.0 : (double) intersection / union;

        return similarity >= Config.SIMILARITY_THRESHOLD;
    }

    // ============================================================================
    // UNION-FIND (Disjoint Set Union)
    // ============================================================================

    private static class UnionFind {
        private final int[] parent;
        private final int[] rank;

        UnionFind(int size) {
            parent = new int[size];
            rank = new int[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;
            }
        }

        int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]); // Path compression
            }
            return parent[x];
        }

        void union(int x, int y) {
            int rootX = find(x);
            int rootY = find(y);

            if (rootX == rootY) return;

            // Union by rank
            if (rank[rootX] < rank[rootY]) {
                parent[rootX] = rootY;
            } else if (rank[rootX] > rank[rootY]) {
                parent[rootY] = rootX;
            } else {
                parent[rootY] = rootX;
                rank[rootX]++;
            }
        }
    }

    // ============================================================================
    // DISPLAY
    // ============================================================================

    private void displayClusters(List<List<Article>> clusters) {
        int clusterNum = 1;
        for (List<Article> cluster : clusters) {
            Article canonical = cluster.get(0);

            // Cluster header
            String timestamp = canonical.getEffectiveDate().format(DISPLAY_DATE_FMT);
            System.out.println("[" + clusterNum++ + "] " + timestamp +
                             " • CANONICAL (" + canonical.sourceType() + ") " +
                             canonical.domain());
            System.out.println("     " + canonical.title());
            System.out.println("     " + canonical.canonicalUrl());

            // Members
            if (cluster.size() > 1) {
                System.out.println("     Members (" + cluster.size() + "):");
                for (Article article : cluster) {
                    String marker = article.equals(canonical) ? "★" : "•";
                    String time = article.getEffectiveDate().format(SHORT_DATE_FMT);
                    String title = truncate(article.title(), 90);

                    System.out.printf("       %s %-11s %-20s %s  %s%n",
                        marker, article.sourceType(), article.domain(), time, title);
                }
            }
            System.out.println();
        }
    }

    private void displayMetrics(int totalArticles, List<List<Article>> clusters) {
        double avgSize = clusters.stream()
            .mapToInt(List::size)
            .average()
            .orElse(0.0);

        System.out.println("=== METRICS ===");
        System.out.printf("Articles fetched:   %d%n", totalArticles);
        System.out.printf("Story clusters:     %d%n", clusters.size());
        System.out.printf("Avg items/cluster:  %.2f%n", avgSize);
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 1) + "…";
    }
}
