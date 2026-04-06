package com.bloomfilter.bloomfilter.core;

import java.nio.charset.StandardCharsets;

/**
 * Murmur3 32-bit hash function — implemented entirely from scratch.
 *
 * <p>MurmurHash3 was created by Austin Appleby and placed in the public domain.
 * This is a faithful Java implementation of the 32-bit variant, optimized for
 * use in Bloom Filters via the double-hashing technique.</p>
 *
 * <h3>Double Hashing Technique</h3>
 * <p>Instead of computing k independent hash functions, we compute only 2 Murmur3
 * hashes with different seeds and derive k hashes via:</p>
 * <pre>
 *   h(i) = (h1 + i * h2) mod m
 * </pre>
 * <p>This is mathematically proven to be as effective as k truly independent hash
 * functions for Bloom Filters (Kirsch & Mitzenmacher, 2006).</p>
 *
 * @author Built from scratch — no Guava, no Commons, no external libraries
 */
public class Murmur3HashStrategy {

    private static final int SEED_1 = 0;
    private static final int SEED_2 = 0x9747b28c;

    // Murmur3 mixing constants
    private static final int C1 = 0xcc9e2d51;
    private static final int C2 = 0x1b873593;

    /**
     * Computes {@code numHashes} hash positions for the given element using double hashing.
     *
     * @param element    the element to hash
     * @param numHashes  number of hash functions (k)
     * @param bitSize    size of the bit array (m)
     * @return array of k hash positions, each in [0, bitSize)
     */
    public int[] computeHashes(String element, int numHashes, int bitSize) {
        byte[] data = element.getBytes(StandardCharsets.UTF_8);

        int h1 = murmur3_32(data, SEED_1);
        int h2 = murmur3_32(data, SEED_2);

        int[] hashes = new int[numHashes];
        for (int i = 0; i < numHashes; i++) {
            int combinedHash = h1 + (i * h2);
            // Ensure positive value and fit within bit array bounds
            hashes[i] = ((combinedHash % bitSize) + bitSize) % bitSize;
        }

        return hashes;
    }

    /**
     * Murmur3 32-bit hash function.
     *
     * <p>Processes the input in 4-byte chunks, applying mixing operations to each chunk.
     * Remaining bytes (< 4) are handled in a tail section. A finalization mix ensures
     * all bits of the hash block avalanche properly.</p>
     *
     * @param data the byte array to hash
     * @param seed the hash seed (different seeds produce different hash functions)
     * @return 32-bit Murmur3 hash value
     */
    static int murmur3_32(byte[] data, int seed) {
        int h = seed;
        int len = data.length;
        int i = 0;

        // ---- Body: Process 4-byte chunks ----
        while (i + 4 <= len) {
            int k = (data[i] & 0xFF)
                    | ((data[i + 1] & 0xFF) << 8)
                    | ((data[i + 2] & 0xFF) << 16)
                    | ((data[i + 3] & 0xFF) << 24);

            k *= C1;
            k = Integer.rotateLeft(k, 15);
            k *= C2;

            h ^= k;
            h = Integer.rotateLeft(h, 13);
            h = h * 5 + 0xe6546b64;

            i += 4;
        }

        // ---- Tail: Process remaining bytes ----
        int remaining = 0;
        switch (len - i) {
            case 3:
                remaining ^= (data[i + 2] & 0xFF) << 16;
                // fall through
            case 2:
                remaining ^= (data[i + 1] & 0xFF) << 8;
                // fall through
            case 1:
                remaining ^= (data[i] & 0xFF);
                remaining *= C1;
                remaining = Integer.rotateLeft(remaining, 15);
                remaining *= C2;
                h ^= remaining;
        }

        // ---- Finalization: Force all bits to avalanche ----
        h ^= len;
        h = fmix32(h);

        return h;
    }

    /**
     * Finalization mix — forces all bits of a hash block to avalanche.
     * Ensures that a small change in input produces a large change in output.
     */
    private static int fmix32(int h) {
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }
}
