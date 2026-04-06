package com.bloomfilter.bloomfilter.service;

import com.bloomfilter.bloomfilter.core.BloomFilter;
import com.bloomfilter.bloomfilter.dto.BloomFilterStatsResponse;
import com.bloomfilter.bloomfilter.exception.FilterAlreadyExistsException;
import com.bloomfilter.bloomfilter.exception.FilterNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central service managing a registry of named Bloom Filters.
 *
 * <p>Provides CRUD operations for Bloom Filters and delegates to the
 * core {@link BloomFilter} engine for membership testing.</p>
 *
 * <p>Thread-safe — the registry itself uses {@link ConcurrentHashMap},
 * and each individual filter is internally thread-safe.</p>
 */
public class BloomFilterService {

    private static final Logger log = LoggerFactory.getLogger(BloomFilterService.class);

    /** Registry of all named Bloom Filters. */
    private final Map<String, BloomFilter> filterRegistry = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════
    //  Filter Lifecycle
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates and registers a new Bloom Filter.
     *
     * @param name                     unique filter name
     * @param expectedInsertions       expected number of elements
     * @param falsePositiveProbability  target false positive rate
     * @return the created BloomFilter
     * @throws FilterAlreadyExistsException if filter with this name already exists
     */
    public BloomFilter createFilter(String name, long expectedInsertions, double falsePositiveProbability) {
        if (filterRegistry.containsKey(name)) {
            throw new FilterAlreadyExistsException(name);
        }

        BloomFilter filter = new BloomFilter(expectedInsertions, falsePositiveProbability);
        filterRegistry.put(name, filter);

        log.info("Created Bloom Filter '{}' — m={} bits, k={} hashes, target FPP={}",
                name, filter.getBitSize(), filter.getNumHashFunctions(), falsePositiveProbability);

        return filter;
    }

    /**
     * Retrieves a filter by name, throwing if not found.
     */
    public BloomFilter getFilter(String name) {
        BloomFilter filter = filterRegistry.get(name);
        if (filter == null) {
            throw new FilterNotFoundException(name);
        }
        return filter;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Core Operations
    // ═══════════════════════════════════════════════════════════════

    /**
     * Adds an element to the specified Bloom Filter.
     */
    public void add(String filterName, String element) {
        BloomFilter filter = getFilter(filterName);
        filter.add(element);
        log.debug("Added element to filter '{}': {}", filterName, element);
    }

    /**
     * Checks if an element might exist in the specified Bloom Filter.
     *
     * @return {@code true} if possibly present, {@code false} if definitely absent
     */
    public boolean mightContain(String filterName, String element) {
        BloomFilter filter = getFilter(filterName);
        boolean result = filter.mightContain(element);
        log.debug("Check filter '{}' for '{}': {}", filterName, element,
                result ? "POSSIBLY_PRESENT" : "DEFINITELY_ABSENT");
        return result;
    }

    /**
     * Resets the specified Bloom Filter, clearing all bits.
     */
    public void reset(String filterName) {
        BloomFilter filter = getFilter(filterName);
        filter.clear();
        log.info("Reset Bloom Filter '{}'", filterName);
    }

    /**
     * Removes a filter from the registry entirely.
     */
    public void deleteFilter(String filterName) {
        if (filterRegistry.remove(filterName) == null) {
            throw new FilterNotFoundException(filterName);
        }
        log.info("Deleted Bloom Filter '{}'", filterName);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Statistics
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns detailed statistics for a Bloom Filter.
     */
    public BloomFilterStatsResponse getStats(String filterName) {
        BloomFilter filter = getFilter(filterName);
        return BloomFilterStatsResponse.from(
                filterName,
                filter.getExpectedInsertions(),
                filter.getInsertionCount(),
                filter.getBitSize(),
                filter.getNumHashFunctions(),
                filter.getBitsSet(),
                filter.getConfiguredFalsePositiveProbability(),
                filter.getEstimatedFalsePositiveProbability(),
                filter.getSaturationRatio(),
                filter.getMemoryUsageBytes()
        );
    }

    /**
     * Returns the set of all registered filter names.
     */
    public Set<String> getFilterNames() {
        return Collections.unmodifiableSet(filterRegistry.keySet());
    }

    /**
     * Returns the total number of registered filters.
     */
    public int getFilterCount() {
        return filterRegistry.size();
    }

    /**
     * Checks if a filter exists.
     */
    public boolean filterExists(String name) {
        return filterRegistry.containsKey(name);
    }
}
