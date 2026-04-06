package com.bloomfilter.bloomfilter.service;

import com.bloomfilter.bloomfilter.dto.BloomFilterStatsResponse;
import com.bloomfilter.bloomfilter.exception.FilterAlreadyExistsException;
import com.bloomfilter.bloomfilter.exception.FilterNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BloomFilterService.
 */
class BloomFilterServiceTest {

    private BloomFilterService service;

    @BeforeEach
    void setUp() {
        service = new BloomFilterService();
    }

    @Nested
    @DisplayName("Filter Lifecycle")
    class FilterLifecycle {

        @Test
        @DisplayName("Should create and retrieve a filter")
        void createAndRetrieve() {
            service.createFilter("test-filter", 1000, 0.01);
            assertTrue(service.filterExists("test-filter"));
            assertEquals(1, service.getFilterCount());
        }

        @Test
        @DisplayName("Should throw on duplicate filter creation")
        void duplicateCreation() {
            service.createFilter("test-filter", 1000, 0.01);
            assertThrows(FilterAlreadyExistsException.class,
                    () -> service.createFilter("test-filter", 1000, 0.01));
        }

        @Test
        @DisplayName("Should delete a filter")
        void deleteFilter() {
            service.createFilter("test-filter", 1000, 0.01);
            service.deleteFilter("test-filter");
            assertFalse(service.filterExists("test-filter"));
        }

        @Test
        @DisplayName("Should throw on deleting non-existent filter")
        void deleteNonExistent() {
            assertThrows(FilterNotFoundException.class,
                    () -> service.deleteFilter("non-existent"));
        }

        @Test
        @DisplayName("Should list all filter names")
        void listFilterNames() {
            service.createFilter("filter-a", 1000, 0.01);
            service.createFilter("filter-b", 2000, 0.05);
            assertEquals(2, service.getFilterNames().size());
            assertTrue(service.getFilterNames().contains("filter-a"));
            assertTrue(service.getFilterNames().contains("filter-b"));
        }
    }

    @Nested
    @DisplayName("Core Operations")
    class CoreOperations {

        @BeforeEach
        void setUpFilter() {
            service.createFilter("my-filter", 10_000, 0.01);
        }

        @Test
        @DisplayName("Should add and check elements")
        void addAndCheck() {
            service.add("my-filter", "hello");
            assertTrue(service.mightContain("my-filter", "hello"));
            assertFalse(service.mightContain("my-filter", "world"));
        }

        @Test
        @DisplayName("Should throw when operating on non-existent filter")
        void operateOnNonExistent() {
            assertThrows(FilterNotFoundException.class,
                    () -> service.add("ghost", "element"));
            assertThrows(FilterNotFoundException.class,
                    () -> service.mightContain("ghost", "element"));
        }

        @Test
        @DisplayName("Should reset a filter")
        void resetFilter() {
            service.add("my-filter", "hello");
            assertTrue(service.mightContain("my-filter", "hello"));

            service.reset("my-filter");

            assertFalse(service.mightContain("my-filter", "hello"));
        }
    }

    @Nested
    @DisplayName("Statistics")
    class Statistics {

        @Test
        @DisplayName("Should return accurate stats")
        void accurateStats() {
            service.createFilter("stats-filter", 1000, 0.01);
            service.add("stats-filter", "a");
            service.add("stats-filter", "b");

            BloomFilterStatsResponse stats = service.getStats("stats-filter");

            assertEquals("stats-filter", stats.filterName());
            assertEquals(1000, stats.expectedInsertions());
            assertEquals(2, stats.actualInsertions());
            assertEquals(0.01, stats.configuredFpp());
            assertTrue(stats.bitArraySize() > 0);
            assertTrue(stats.numHashFunctions() > 0);
            assertTrue(stats.bitsSet() > 0);
            assertNotNull(stats.healthStatus());
            assertNotNull(stats.memoryUsage());
        }
    }
}
