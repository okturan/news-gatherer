package com.newsgatherer.presentation.console;

import com.newsgatherer.application.dto.ClusteringResultDto;
import com.newsgatherer.application.usecase.GatherAndDeduplicateArticlesUseCase;
import com.newsgatherer.config.ApplicationConfig;
import com.newsgatherer.domain.repository.ArticleRepositoryException;

/**
 * Main console application entry point.
 */
public class ConsoleApplication {
    private final GatherAndDeduplicateArticlesUseCase useCase;
    private final ClusterFormatter clusterFormatter;
    private final MetricsPresenter metricsPresenter;

    public ConsoleApplication(
            GatherAndDeduplicateArticlesUseCase useCase,
            ClusterFormatter clusterFormatter,
            MetricsPresenter metricsPresenter) {
        this.useCase = useCase;
        this.clusterFormatter = clusterFormatter;
        this.metricsPresenter = metricsPresenter;
    }

    /**
     * Runs the application with Turkish news query.
     */
    public void run() {
        String query = "sourcecountry:turkey sourcelang:turkish";

        try {
            ClusteringResultDto result = useCase.execute(query);

            if (result.isEmpty()) {
                System.out.println("No articles found.");
                return;
            }

            clusterFormatter.formatClusters(result.clusters());
            metricsPresenter.presentMetrics(result);

        } catch (ArticleRepositoryException e) {
            System.err.println("Error gathering articles: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        ApplicationConfig config = new ApplicationConfig();
        ConsoleApplication app = config.createConsoleApplication();
        app.run();
    }
}
