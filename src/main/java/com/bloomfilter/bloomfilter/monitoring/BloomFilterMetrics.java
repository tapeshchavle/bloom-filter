package com.bloomfilter.bloomfilter.monitoring;

import com.bloomfilter.bloomfilter.service.BloomFilterService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Micrometer metrics for Bloom Filter observability.
 *
 * <p>Exposes the following metrics per filter:</p>
 * <ul>
 *   <li>{@code bloom.filter.insertions} — number of elements inserted</li>
 *   <li>{@code bloom.filter.saturation} — percentage of bits set [0-1]</li>
 *   <li>{@code bloom.filter.estimated.fpp} — current estimated false positive probability</li>
 *   <li>{@code bloom.filter.bit.size} — total bit array size</li>
 * </ul>
 *
 * <p>Accessible at: {@code GET /actuator/metrics/bloom.filter.*}</p>
 * <p>Prometheus: {@code GET /actuator/prometheus}</p>
 */
@Component
public class BloomFilterMetrics {

    private final BloomFilterService bloomFilterService;
    private final MeterRegistry meterRegistry;

    public BloomFilterMetrics(BloomFilterService bloomFilterService, MeterRegistry meterRegistry) {
        this.bloomFilterService = bloomFilterService;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void registerMetrics() {
        registerFilters();
    }

    /**
     * Periodically re-registers metrics for any dynamically created filters.
     */
    @Scheduled(fixedRate = 60_000) // every 60 seconds
    public void refreshMetrics() {
        registerFilters();
    }

    private void registerFilters() {
        for (String filterName : bloomFilterService.getFilterNames()) {
            String sanitizedName = filterName.replace("-", "_");

            // Insertions gauge
            Gauge.builder("bloom.filter.insertions", bloomFilterService,
                            svc -> svc.filterExists(filterName)
                                    ? svc.getStats(filterName).actualInsertions()
                                    : 0)
                    .tag("filter", filterName)
                    .description("Number of elements inserted into the Bloom Filter")
                    .register(meterRegistry);

            // Saturation gauge
            Gauge.builder("bloom.filter.saturation", bloomFilterService,
                            svc -> svc.filterExists(filterName)
                                    ? svc.getStats(filterName).saturationPercent() / 100.0
                                    : 0)
                    .tag("filter", filterName)
                    .description("Saturation ratio of the Bloom Filter bit array")
                    .register(meterRegistry);

            // Estimated FPP gauge
            Gauge.builder("bloom.filter.estimated.fpp", bloomFilterService,
                            svc -> svc.filterExists(filterName)
                                    ? svc.getStats(filterName).estimatedFpp()
                                    : 0)
                    .tag("filter", filterName)
                    .description("Current estimated false positive probability")
                    .register(meterRegistry);

            // Bit array size gauge
            Gauge.builder("bloom.filter.bit.size", bloomFilterService,
                            svc -> svc.filterExists(filterName)
                                    ? svc.getStats(filterName).bitArraySize()
                                    : 0)
                    .tag("filter", filterName)
                    .description("Total size of the bit array")
                    .register(meterRegistry);
        }
    }
}
