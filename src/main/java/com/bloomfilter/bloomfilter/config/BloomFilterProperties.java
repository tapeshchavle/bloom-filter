package com.bloomfilter.bloomfilter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Externalized Bloom Filter configuration via application.yml.
 *
 * <p>Example configuration:</p>
 * <pre>
 * bloom:
 *   filters:
 *     username-registry:
 *       expected-insertions: 10000000
 *       false-positive-probability: 0.001
 * </pre>
 */
@ConfigurationProperties(prefix = "bloom")
public class BloomFilterProperties {

    /**
     * Map of named filter configurations.
     * Key = filter name, Value = filter parameters.
     */
    private Map<String, FilterConfig> filters = new HashMap<>();

    public Map<String, FilterConfig> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, FilterConfig> filters) {
        this.filters = filters;
    }

    /**
     * Configuration for a single Bloom Filter instance.
     */
    public static class FilterConfig {

        /** Expected number of elements to be inserted into this filter. */
        private long expectedInsertions = 1_000_000;

        /** Target false positive probability (e.g., 0.01 = 1%). */
        private double falsePositiveProbability = 0.01;

        public long getExpectedInsertions() {
            return expectedInsertions;
        }

        public void setExpectedInsertions(long expectedInsertions) {
            this.expectedInsertions = expectedInsertions;
        }

        public double getFalsePositiveProbability() {
            return falsePositiveProbability;
        }

        public void setFalsePositiveProbability(double falsePositiveProbability) {
            this.falsePositiveProbability = falsePositiveProbability;
        }
    }
}
