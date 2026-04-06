package com.bloomfilter.bloomfilter.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for the core Bloom Filter implementation.
 *
 * Tests cover:
 * - Basic add/check operations
 * - Zero false negatives guarantee
 * - False positive rate validation
 * - Optimal parameter calculation
 * - Thread safety under concurrent access
 * - Edge cases and error handling
 */
class BloomFilterTest {

    private BloomFilter filter;

    @BeforeEach
    void setUp() {
        // Default: 100K expected insertions, 1% FPP
        filter = new BloomFilter(100_000, 0.01);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Basic Operations
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {

        @Test
        @DisplayName("Added element should be found by mightContain")
        void addAndCheck() {
            filter.add("hello");
            assertTrue(filter.mightContain("hello"));
        }

        @Test
        @DisplayName("Non-added element should not be found")
        void checkAbsentElement() {
            filter.add("hello");
            // A non-added element should almost certainly not be found
            // (with 100K capacity and only 1 insertion, FPP is negligible)
            assertFalse(filter.mightContain("world"));
        }

        @Test
        @DisplayName("Multiple elements can be added and checked")
        void addMultipleElements() {
            String[] elements = {"apple", "banana", "cherry", "date", "elderberry"};
            for (String e : elements) {
                filter.add(e);
            }
            for (String e : elements) {
                assertTrue(filter.mightContain(e), "Should find: " + e);
            }
        }

        @Test
        @DisplayName("Insertion count should be accurate")
        void insertionCountAccurate() {
            assertEquals(0, filter.getInsertionCount());
            filter.add("a");
            filter.add("b");
            filter.add("c");
            assertEquals(3, filter.getInsertionCount());
        }

        @Test
        @DisplayName("Adding same element multiple times increments count")
        void duplicateInsertionIncrementsCount() {
            filter.add("same");
            filter.add("same");
            filter.add("same");
            assertEquals(3, filter.getInsertionCount());
            assertTrue(filter.mightContain("same"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Zero False Negatives Guarantee
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Zero False Negatives")
    class ZeroFalseNegatives {

        @Test
        @DisplayName("Every added element must be found — ZERO false negatives")
        void noFalseNegatives() {
            int count = 10_000;
            Set<String> inserted = new HashSet<>();

            for (int i = 0; i < count; i++) {
                String element = "element_" + i;
                filter.add(element);
                inserted.add(element);
            }

            // Every single inserted element MUST be found
            int falseNegatives = 0;
            for (String element : inserted) {
                if (!filter.mightContain(element)) {
                    falseNegatives++;
                }
            }

            assertEquals(0, falseNegatives,
                    "Bloom Filter must have ZERO false negatives, but found: " + falseNegatives);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  False Positive Rate Validation
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("False Positive Rate")
    class FalsePositiveRate {

        @Test
        @DisplayName("Actual FPP should be within acceptable range of configured FPP")
        void falsePositiveRateWithinBounds() {
            // Use a smaller filter for faster test
            BloomFilter smallFilter = new BloomFilter(10_000, 0.01);

            // Insert n elements
            for (int i = 0; i < 10_000; i++) {
                smallFilter.add("inserted_" + i);
            }

            // Check 100K elements that were NOT inserted
            int falsePositives = 0;
            int testCount = 100_000;
            for (int i = 0; i < testCount; i++) {
                if (smallFilter.mightContain("NOT_inserted_" + i)) {
                    falsePositives++;
                }
            }

            double actualFpp = (double) falsePositives / testCount;

            // Allow up to 2x the configured FPP as tolerance
            // (statistical variance is expected)
            assertTrue(actualFpp < 0.02,
                    "Actual FPP (" + String.format("%.4f", actualFpp) +
                    ") should be close to configured FPP (0.01). " +
                    "False positives: " + falsePositives + "/" + testCount);

            System.out.println("📊 FPP Test Results:");
            System.out.println("   Configured FPP: 0.01 (1%)");
            System.out.println("   Actual FPP:     " + String.format("%.6f", actualFpp) +
                    " (" + String.format("%.4f%%", actualFpp * 100) + ")");
            System.out.println("   False positives: " + falsePositives + " / " + testCount);
        }

        @Test
        @DisplayName("Estimated FPP should increase as filter fills up")
        void estimatedFppIncreasesWithInsertions() {
            double fppBefore = filter.getEstimatedFalsePositiveProbability();
            assertEquals(0.0, fppBefore);

            // Insert elements
            for (int i = 0; i < 1000; i++) {
                filter.add("element_" + i);
            }

            double fppAfter = filter.getEstimatedFalsePositiveProbability();
            assertTrue(fppAfter > 0, "FPP should increase after insertions");
            assertTrue(fppAfter < filter.getConfiguredFalsePositiveProbability(),
                    "FPP should still be below configured threshold with only 1% capacity used");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Parameter Calculation
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Optimal Parameter Calculation")
    class ParameterCalculation {

        @Test
        @DisplayName("Bit array size should be optimal for given n and p")
        void optimalBitSize() {
            // For n=1M, p=0.01: m ≈ 9,585,059
            int m = BloomFilter.calculateOptimalBitSize(1_000_000, 0.01);
            assertTrue(m > 9_000_000 && m < 10_000_000,
                    "Bit size for 1M/1% should be ~9.6M, got: " + m);
        }

        @Test
        @DisplayName("Hash count should be optimal for given m and n")
        void optimalHashCount() {
            // For m/n ratio of ~9.58: k ≈ 7
            int m = BloomFilter.calculateOptimalBitSize(1_000_000, 0.01);
            int k = BloomFilter.calculateOptimalHashCount(m, 1_000_000);
            assertEquals(7, k, "Optimal hash count for 1M/1% should be 7");
        }

        @Test
        @DisplayName("Lower FPP should require more bits and hash functions")
        void lowerFppRequiresMoreResources() {
            BloomFilter highFpp = new BloomFilter(100_000, 0.1);   // 10% FPP
            BloomFilter lowFpp = new BloomFilter(100_000, 0.001);  // 0.1% FPP

            assertTrue(lowFpp.getBitSize() > highFpp.getBitSize(),
                    "Lower FPP should require more bits");
            assertTrue(lowFpp.getNumHashFunctions() > highFpp.getNumHashFunctions(),
                    "Lower FPP should require more hash functions");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Clear / Reset
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Clear / Reset")
    class ClearReset {

        @Test
        @DisplayName("Clear should remove all elements")
        void clearRemovesAllElements() {
            filter.add("test1");
            filter.add("test2");
            assertTrue(filter.mightContain("test1"));

            filter.clear();

            assertFalse(filter.mightContain("test1"));
            assertFalse(filter.mightContain("test2"));
            assertEquals(0, filter.getInsertionCount());
            assertEquals(0, filter.getBitsSet());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Thread Safety
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafety {

        @Test
        @DisplayName("Concurrent adds and checks should not corrupt the filter")
        void concurrentAccess() throws InterruptedException {
            int threadCount = 10;
            int elementsPerThread = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger(0);

            // Concurrent writers
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < elementsPerThread; i++) {
                            String element = "thread" + threadId + "_element" + i;
                            filter.add(element);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertEquals(0, errors.get(), "No errors should occur during concurrent access");
            assertEquals(threadCount * elementsPerThread, filter.getInsertionCount());

            // Verify all elements are found (zero false negatives)
            for (int t = 0; t < threadCount; t++) {
                for (int i = 0; i < elementsPerThread; i++) {
                    String element = "thread" + t + "_element" + i;
                    assertTrue(filter.mightContain(element),
                            "Should find concurrently added element: " + element);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Statistics
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Statistics")
    class Statistics {

        @Test
        @DisplayName("Saturation should increase with insertions")
        void saturationIncreases() {
            assertEquals(0.0, filter.getSaturationRatio());

            for (int i = 0; i < 10_000; i++) {
                filter.add("sat_" + i);
            }

            double saturation = filter.getSaturationRatio();
            assertTrue(saturation > 0 && saturation < 1,
                    "Saturation should be between 0 and 1, got: " + saturation);
        }

        @Test
        @DisplayName("Memory usage should be reported correctly")
        void memoryUsage() {
            long memory = filter.getMemoryUsageBytes();
            assertTrue(memory > 0, "Memory usage should be positive");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Edge Cases & Error Handling
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should reject null element on add")
        void rejectNullAdd() {
            assertThrows(IllegalArgumentException.class, () -> filter.add(null));
        }

        @Test
        @DisplayName("Should reject null element on check")
        void rejectNullCheck() {
            assertThrows(IllegalArgumentException.class, () -> filter.mightContain(null));
        }

        @Test
        @DisplayName("Should reject zero expected insertions")
        void rejectZeroInsertions() {
            assertThrows(IllegalArgumentException.class, () -> new BloomFilter(0, 0.01));
        }

        @Test
        @DisplayName("Should reject negative expected insertions")
        void rejectNegativeInsertions() {
            assertThrows(IllegalArgumentException.class, () -> new BloomFilter(-1, 0.01));
        }

        @Test
        @DisplayName("Should reject zero FPP")
        void rejectZeroFpp() {
            assertThrows(IllegalArgumentException.class, () -> new BloomFilter(1000, 0));
        }

        @Test
        @DisplayName("Should reject FPP >= 1")
        void rejectFppAboveOne() {
            assertThrows(IllegalArgumentException.class, () -> new BloomFilter(1000, 1.0));
        }

        @Test
        @DisplayName("Empty string should be a valid element")
        void emptyStringIsValid() {
            filter.add("");
            assertTrue(filter.mightContain(""));
        }

        @Test
        @DisplayName("Unicode elements should work correctly")
        void unicodeElements() {
            filter.add("日本語テスト");
            filter.add("emoji: 🎉🚀");
            filter.add("مرحبا بالعالم");

            assertTrue(filter.mightContain("日本語テスト"));
            assertTrue(filter.mightContain("emoji: 🎉🚀"));
            assertTrue(filter.mightContain("مرحبا بالعالم"));
        }
    }
}
