package com.newsgatherer.presentation.console;

import com.newsgatherer.application.dto.ClusteringResultDto;

/**
 * Presents clustering metrics to the console.
 */
public class MetricsPresenter {

    /**
     * Displays clustering metrics.
     */
    public void presentMetrics(ClusteringResultDto result) {
        System.out.println("\n=== METRICS ===");
        System.out.println("Articles fetched:   " + result.totalArticles());
        System.out.println("Story clusters:     " + result.totalClusters());
        System.out.printf("Avg items/cluster:  %.2f%n", result.averageClusterSize());
    }
}
