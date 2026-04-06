package com.bloomfilter.bloomfilter.config;

import com.bloomfilter.bloomfilter.service.BloomFilterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration that creates and registers Bloom Filters
 * defined in application.yml at startup.
 */
@Configuration
@EnableConfigurationProperties(BloomFilterProperties.class)
public class BloomFilterAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BloomFilterAutoConfiguration.class);

    @Bean
    public BloomFilterService bloomFilterService(BloomFilterProperties properties) {
        BloomFilterService service = new BloomFilterService();

        properties.getFilters().forEach((name, config) -> {
            service.createFilter(name, config.getExpectedInsertions(), config.getFalsePositiveProbability());
            log.info("✅ Registered Bloom Filter '{}' — expectedInsertions={}, fpp={}",
                    name, config.getExpectedInsertions(), config.getFalsePositiveProbability());
        });

        log.info("🚀 Bloom Filter Service initialized with {} pre-configured filters",
                properties.getFilters().size());

        return service;
    }
}
