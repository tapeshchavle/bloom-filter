package com.bloomfilter.bloomfilter.monitoring;

import com.bloomfilter.bloomfilter.service.BloomFilterService;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator Health Indicator for Bloom Filters.
 *
 * <p>Reports the health status of all registered Bloom Filters based on
 * their saturation levels. A filter is considered unhealthy when its
 * saturation exceeds 80%, as the false positive rate degrades rapidly
 * beyond that point.</p>
 *
 * <p>Accessible at: {@code GET /actuator/health}</p>
 */
@Component
public class BloomFilterHealthIndicator implements HealthIndicator {

    private final BloomFilterService bloomFilterService;

    public BloomFilterHealthIndicator(BloomFilterService bloomFilterService) {
        this.bloomFilterService = bloomFilterService;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        boolean hasWarning = false;
        boolean hasCritical = false;

        builder.withDetail("totalFilters", bloomFilterService.getFilterCount());

        for (String filterName : bloomFilterService.getFilterNames()) {
            try {
                var stats = bloomFilterService.getStats(filterName);
                String status;
                if (stats.saturationPercent() < 50.0) {
                    status = "HEALTHY";
                } else if (stats.saturationPercent() < 80.0) {
                    status = "WARNING";
                    hasWarning = true;
                } else {
                    status = "CRITICAL";
                    hasCritical = true;
                }

                builder.withDetail("filter." + filterName + ".status", status);
                builder.withDetail("filter." + filterName + ".saturation",
                        stats.saturationPercent() + "%");
                builder.withDetail("filter." + filterName + ".estimatedFpp",
                        stats.estimatedFpp());
                builder.withDetail("filter." + filterName + ".insertions",
                        stats.actualInsertions() + " / " + stats.expectedInsertions());
            } catch (Exception e) {
                builder.withDetail("filter." + filterName + ".error", e.getMessage());
                hasCritical = true;
            }
        }

        if (hasCritical) {
            return builder.down()
                    .withDetail("recommendation",
                            "One or more filters are critically saturated. Reset or increase capacity.")
                    .build();
        } else if (hasWarning) {
            return builder.up()
                    .withDetail("recommendation",
                            "Some filters are moderately saturated. Monitor closely.")
                    .build();
        }

        return builder.build();
    }
}
