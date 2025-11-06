package com.newsgatherer.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.newsgatherer.application.service.ArticleDeduplicationService;
import com.newsgatherer.application.service.ArticleGatheringService;
import com.newsgatherer.application.usecase.GatherAndDeduplicateArticlesUseCase;
import com.newsgatherer.domain.repository.ArticleRepository;
import com.newsgatherer.domain.service.ArticleSimilarityService;
import com.newsgatherer.domain.service.CanonicalSelectionService;
import com.newsgatherer.domain.service.ClusteringService;
import com.newsgatherer.infrastructure.clustering.TimeWindowedClusteringService;
import com.newsgatherer.infrastructure.http.GdeltApiClient;
import com.newsgatherer.infrastructure.http.GdeltArticleParser;
import com.newsgatherer.infrastructure.http.GdeltArticleRepository;
import com.newsgatherer.infrastructure.selection.SourceTypeCanonicalSelector;
import com.newsgatherer.infrastructure.similarity.JaccardSimilarityService;
import com.newsgatherer.presentation.console.ClusterFormatter;
import com.newsgatherer.presentation.console.ConsoleApplication;
import com.newsgatherer.presentation.console.MetricsPresenter;

/**
 * Application configuration and dependency injection.
 * Manually wires all dependencies following dependency inversion principle.
 */
public class ApplicationConfig {

    // Configuration objects
    private final GdeltApiConfig gdeltApiConfig;
    private final ClusteringConfig clusteringConfig;
    private final TurkishLanguageConfig turkishLanguageConfig;

    // Singletons
    private final ObjectMapper objectMapper;

    public ApplicationConfig() {
        // Initialize configurations with defaults
        this.gdeltApiConfig = GdeltApiConfig.builder().build();
        this.clusteringConfig = ClusteringConfig.builder().build();
        this.turkishLanguageConfig = new TurkishLanguageConfig();

        // Initialize shared dependencies
        this.objectMapper = createObjectMapper();
    }

    /**
     * Creates the main console application with all dependencies wired.
     */
    public ConsoleApplication createConsoleApplication() {
        // Infrastructure layer
        GdeltApiClient apiClient = createApiClient();
        GdeltArticleParser articleParser = createArticleParser();
        ArticleRepository articleRepository = createArticleRepository(apiClient, articleParser);

        ArticleSimilarityService similarityService = createSimilarityService();
        ClusteringService clusteringService = createClusteringService(similarityService);
        CanonicalSelectionService canonicalSelectionService = createCanonicalSelectionService();

        // Application layer
        ArticleGatheringService gatheringService =
            createGatheringService(articleRepository);

        ArticleDeduplicationService deduplicationService =
            createDeduplicationService(clusteringService, canonicalSelectionService);

        GatherAndDeduplicateArticlesUseCase useCase =
            createUseCase(gatheringService, deduplicationService);

        // Presentation layer
        ClusterFormatter clusterFormatter = new ClusterFormatter();
        MetricsPresenter metricsPresenter = new MetricsPresenter();

        return new ConsoleApplication(useCase, clusterFormatter, metricsPresenter);
    }

    // Factory methods for infrastructure components

    private GdeltApiClient createApiClient() {
        return new GdeltApiClient(gdeltApiConfig, objectMapper);
    }

    private GdeltArticleParser createArticleParser() {
        return new GdeltArticleParser(turkishLanguageConfig, clusteringConfig);
    }

    private ArticleRepository createArticleRepository(
            GdeltApiClient apiClient,
            GdeltArticleParser articleParser) {
        return new GdeltArticleRepository(apiClient, articleParser);
    }

    private ArticleSimilarityService createSimilarityService() {
        return new JaccardSimilarityService();
    }

    private ClusteringService createClusteringService(
            ArticleSimilarityService similarityService) {
        return new TimeWindowedClusteringService(similarityService);
    }

    private CanonicalSelectionService createCanonicalSelectionService() {
        return new SourceTypeCanonicalSelector();
    }

    // Factory methods for application components

    private ArticleGatheringService createGatheringService(
            ArticleRepository articleRepository) {
        return new ArticleGatheringService(articleRepository, gdeltApiConfig);
    }

    private ArticleDeduplicationService createDeduplicationService(
            ClusteringService clusteringService,
            CanonicalSelectionService canonicalSelectionService) {
        return new ArticleDeduplicationService(
            clusteringService,
            canonicalSelectionService,
            clusteringConfig
        );
    }

    private GatherAndDeduplicateArticlesUseCase createUseCase(
            ArticleGatheringService gatheringService,
            ArticleDeduplicationService deduplicationService) {
        return new GatherAndDeduplicateArticlesUseCase(
            gatheringService,
            deduplicationService
        );
    }

    /**
     * Creates a configured Jackson ObjectMapper for JSON processing.
     */
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    // Getters for testing purposes

    public GdeltApiConfig getGdeltApiConfig() {
        return gdeltApiConfig;
    }

    public ClusteringConfig getClusteringConfig() {
        return clusteringConfig;
    }

    public TurkishLanguageConfig getTurkishLanguageConfig() {
        return turkishLanguageConfig;
    }
}
