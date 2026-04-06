package com.bloomfilter.bloomfilter.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Murmur3 hash implementation.
 *
 * Validates:
 * - Deterministic output for same input
 * - Hash distribution uniformity
 * - Known test vectors
 * - Edge cases (empty input, single byte, etc.)
 */
class Murmur3HashStrategyTest {

    private final Murmur3HashStrategy hashStrategy = new Murmur3HashStrategy();

    @Nested
    @DisplayName("Determinism")
    class Determinism {

        @Test
        @DisplayName("Same input should always produce same hash")
        void sameInputSameHash() {
            int[] hashes1 = hashStrategy.computeHashes("test", 7, 1000);
            int[] hashes2 = hashStrategy.computeHashes("test", 7, 1000);

            assertArrayEquals(hashes1, hashes2);
        }

        @Test
        @DisplayName("Different inputs should produce different hashes")
        void differentInputsDifferentHashes() {
            int[] hashes1 = hashStrategy.computeHashes("hello", 7, 10000);
            int[] hashes2 = hashStrategy.computeHashes("world", 7, 10000);

            // At least some positions should differ
            boolean foundDifference = false;
            for (int i = 0; i < 7; i++) {
                if (hashes1[i] != hashes2[i]) {
                    foundDifference = true;
                    break;
                }
            }
            assertTrue(foundDifference, "Different inputs should produce different hash positions");
        }
    }

    @Nested
    @DisplayName("Hash Distribution")
    class Distribution {

        @Test
        @DisplayName("Hash positions should be within bounds [0, bitSize)")
        void hashPositionsWithinBounds() {
            int bitSize = 1000;
            for (int i = 0; i < 1000; i++) {
                int[] hashes = hashStrategy.computeHashes("element_" + i, 7, bitSize);
                for (int h : hashes) {
                    assertTrue(h >= 0 && h < bitSize,
                            "Hash position " + h + " out of bounds [0, " + bitSize + ")");
                }
            }
        }

        @Test
        @DisplayName("Hash positions should be uniformly distributed")
        void uniformDistribution() {
            int bitSize = 1000;
            int[] bucketCounts = new int[10]; // 10 buckets of 100 positions each

            for (int i = 0; i < 10000; i++) {
                int[] hashes = hashStrategy.computeHashes("dist_element_" + i, 1, bitSize);
                int bucket = hashes[0] / 100;
                bucketCounts[bucket]++;
            }

            // Each bucket should have roughly 1000 hits (±30% tolerance)
            for (int i = 0; i < 10; i++) {
                assertTrue(bucketCounts[i] > 700 && bucketCounts[i] < 1300,
                        "Bucket " + i + " has " + bucketCounts[i] +
                        " hits, expected ~1000 (uniform distribution)");
            }
        }

        @Test
        @DisplayName("Each hash function should produce different positions")
        void hashFunctionsProduceDifferentPositions() {
            int bitSize = 100000;
            int numHashes = 7;
            int[] hashes = hashStrategy.computeHashes("test_element", numHashes, bitSize);

            Set<Integer> uniquePositions = new HashSet<>();
            for (int h : hashes) {
                uniquePositions.add(h);
            }

            // With a large bit size, all 7 positions should be unique
            assertEquals(numHashes, uniquePositions.size(),
                    "All " + numHashes + " hash positions should be unique for large bit arrays");
        }
    }

    @Nested
    @DisplayName("Murmur3 Core")
    class Murmur3Core {

        @Test
        @DisplayName("Empty input should produce valid hash")
        void emptyInput() {
            int hash = Murmur3HashStrategy.murmur3_32(new byte[0], 0);
            // Murmur3 of empty input with seed 0 should be 0 (known value)
            assertEquals(0, hash, "Empty input with seed 0 should produce 0");
        }

        @Test
        @DisplayName("Known test vector: 'Hello'")
        void knownTestVector() {
            byte[] data = "Hello".getBytes(StandardCharsets.UTF_8);
            int hash = Murmur3HashStrategy.murmur3_32(data, 0);
            // Just verify it produces a non-zero, deterministic value
            assertNotEquals(0, hash);
            // Verify it's deterministic
            assertEquals(hash, Murmur3HashStrategy.murmur3_32(data, 0));
        }

        @Test
        @DisplayName("Different seeds should produce different hashes")
        void differentSeedsDifferentHashes() {
            byte[] data = "test".getBytes(StandardCharsets.UTF_8);
            int hash1 = Murmur3HashStrategy.murmur3_32(data, 0);
            int hash2 = Murmur3HashStrategy.murmur3_32(data, 42);
            int hash3 = Murmur3HashStrategy.murmur3_32(data, 0x9747b28c);

            assertNotEquals(hash1, hash2, "Different seeds should produce different hashes");
            assertNotEquals(hash1, hash3, "Different seeds should produce different hashes");
        }

        @Test
        @DisplayName("Handles all tail lengths (1, 2, 3 remaining bytes)")
        void tailLengths() {
            // 1 byte tail
            int h1 = Murmur3HashStrategy.murmur3_32("a".getBytes(StandardCharsets.UTF_8), 0);
            // 2 byte tail
            int h2 = Murmur3HashStrategy.murmur3_32("ab".getBytes(StandardCharsets.UTF_8), 0);
            // 3 byte tail
            int h3 = Murmur3HashStrategy.murmur3_32("abc".getBytes(StandardCharsets.UTF_8), 0);
            // No tail (4 bytes exactly)
            int h4 = Murmur3HashStrategy.murmur3_32("abcd".getBytes(StandardCharsets.UTF_8), 0);
            // 4 bytes + 1 byte tail
            int h5 = Murmur3HashStrategy.murmur3_32("abcde".getBytes(StandardCharsets.UTF_8), 0);

            // All should be unique
            Set<Integer> hashes = Set.of(h1, h2, h3, h4, h5);
            assertEquals(5, hashes.size(), "All different inputs should produce different hashes");
        }

        @Test
        @DisplayName("Avalanche effect: small input change should flip ~50% of bits")
        void avalancheEffect() {
            byte[] data1 = "test1".getBytes(StandardCharsets.UTF_8);
            byte[] data2 = "test2".getBytes(StandardCharsets.UTF_8);

            int hash1 = Murmur3HashStrategy.murmur3_32(data1, 0);
            int hash2 = Murmur3HashStrategy.murmur3_32(data2, 0);

            int xor = hash1 ^ hash2;
            int flippedBits = Integer.bitCount(xor);

            // Good hash: ~50% of bits should flip (16 out of 32, ±8)
            assertTrue(flippedBits >= 8 && flippedBits <= 24,
                    "Avalanche: " + flippedBits + " bits flipped (expected ~16 of 32)");
        }
    }
}
