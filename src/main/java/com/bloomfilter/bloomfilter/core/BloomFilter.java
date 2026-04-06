package com.bloomfilter.bloomfilter.core;

import java.io.Serial;
import java.io.Serializable;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Production-grade Bloom Filter — built entirely from scratch.
 *
 * <p>A Bloom Filter is a space-efficient probabilistic data structure that tests
 * whether an element is a member of a set. It guarantees:</p>
 * <ul>
 *   <li><b>No false negatives</b> — if it says "not present", it's 100% correct</li>
 *   <li><b>Configurable false positives</b> — if it says "present", there's a small
 *       (configurable) probability it's wrong</li>
 * </ul>
 *
 * <h3>How Instagram Uses This</h3>
 * <ul>
 *   <li><b>Username availability</b> — avoid DB lookup for every keystroke during signup</li>
 *   <li><b>Duplicate post detection</b> — prevent duplicate media uploads</li>
 *   <li><b>Notification dedup</b> — don't spam the same notification twice</li>
 *   <li><b>Feed dedup</b> — don't show the same post again in a user's feed</li>
 * </ul>
 *
 * <h3>Parameter Calculation</h3>
 * <p>Given expected insertions (n) and desired false positive probability (p):</p>
 * <pre>
 *   Bit array size:    m = -(n × ln(p)) / (ln2)²
 *   Hash function count: k = (m / n) × ln2
 * </pre>
 *
 * <h3>Thread Safety</h3>
 * <p>All bit array operations are guarded by a {@link ReentrantReadWriteLock},
 * allowing concurrent reads while serializing writes.</p>
 *
 * @author Built from scratch — no Guava, no Redis Bloom, no external bloom filter libraries
 */
public class BloomFilter implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final BitSet bitSet;
    private final int bitSize;                  // m — size of the bit array
    private final int numHashFunctions;         // k — number of hash functions
    private final long expectedInsertions;      // n — expected number of elements
    private final double falsePositiveProbability; // p — target false positive rate
    private final AtomicLong insertionCount;
    private final ReentrantReadWriteLock lock;
    private final Murmur3HashStrategy hashStrategy;

    /**
     * Creates a new Bloom Filter with automatically calculated optimal parameters.
     *
     * @param expectedInsertions       expected number of elements to insert (n)
     * @param falsePositiveProbability  desired false positive probability, e.g. 0.01 for 1% (p)
     * @throws IllegalArgumentException if parameters are out of valid range
     */
    public BloomFilter(long expectedInsertions, double falsePositiveProbability) {
        validateParameters(expectedInsertions, falsePositiveProbability);

        this.expectedInsertions = expectedInsertions;
        this.falsePositiveProbability = falsePositiveProbability;
        this.bitSize = calculateOptimalBitSize(expectedInsertions, falsePositiveProbability);
        this.numHashFunctions = calculateOptimalHashCount(bitSize, expectedInsertions);
        this.bitSet = new BitSet(bitSize);
        this.insertionCount = new AtomicLong(0);
        this.lock = new ReentrantReadWriteLock();
        this.hashStrategy = new Murmur3HashStrategy();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Core Operations
    // ═══════════════════════════════════════════════════════════════

    /**
     * Adds an element to the Bloom Filter.
     *
     * <p>Computes k hash positions and sets the corresponding bits to 1.
     * This operation is idempotent — adding the same element multiple times
     * has no additional effect on the bit array.</p>
     *
     * @param element the element to add (must not be null)
     */
    public void add(String element) {
        if (element == null) {
            throw new IllegalArgumentException("Element must not be null");
        }

        int[] hashes = hashStrategy.computeHashes(element, numHashFunctions, bitSize);
        lock.writeLock().lock();
        try {
            for (int hash : hashes) {
                bitSet.set(hash);
            }
        } finally {
            lock.writeLock().unlock();
        }
        insertionCount.incrementAndGet();
    }

    /**
     * Tests whether an element might be in the set.
     *
     * <p>Returns {@code false} if the element is <b>definitely NOT</b> in the set
     * (zero false negatives). Returns {@code true} if the element <b>might be</b>
     * in the set, with a probability of false positive equal to the configured FPP.</p>
     *
     * @param element the element to check (must not be null)
     * @return {@code true} if possibly present, {@code false} if definitely absent
     */
    public boolean mightContain(String element) {
        if (element == null) {
            throw new IllegalArgumentException("Element must not be null");
        }

        int[] hashes = hashStrategy.computeHashes(element, numHashFunctions, bitSize);
        lock.readLock().lock();
        try {
            for (int hash : hashes) {
                if (!bitSet.get(hash)) {
                    return false; // Definitely NOT in the set
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        return true; // Probably in the set
    }

    /**
     * Resets the Bloom Filter, clearing all bits and resetting the insertion count.
     * Used for periodic filter rotation (e.g., daily notification dedup reset).
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            bitSet.clear();
        } finally {
            lock.writeLock().unlock();
        }
        insertionCount.set(0);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Statistics & Monitoring
    // ═══════════════════════════════════════════════════════════════

    /**
     * Estimates the current false positive probability based on actual insertions.
     *
     * <p>Formula: (1 - e^(-k × n / m))^k</p>
     * <p>This increases as more elements are inserted and the filter saturates.</p>
     *
     * @return estimated false positive probability [0.0, 1.0]
     */
    public double getEstimatedFalsePositiveProbability() {
        long n = insertionCount.get();
        if (n == 0) return 0.0;
        return Math.pow(
                1.0 - Math.exp(-((double) numHashFunctions * n) / bitSize),
                numHashFunctions
        );
    }

    /**
     * Returns the saturation ratio — percentage of bits set to 1.
     *
     * <p>When saturation exceeds ~50%, the false positive rate starts degrading
     * rapidly. At ~80%, the filter should be considered overloaded and reset
     * or replaced with a larger one.</p>
     *
     * @return saturation ratio [0.0, 1.0]
     */
    public double getSaturationRatio() {
        lock.readLock().lock();
        try {
            return (double) bitSet.cardinality() / bitSize;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the number of bits currently set to 1.
     */
    public int getBitsSet() {
        lock.readLock().lock();
        try {
            return bitSet.cardinality();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns approximate memory usage in bytes.
     */
    public long getMemoryUsageBytes() {
        // BitSet internally uses long[] array
        return (long) Math.ceil(bitSize / 8.0) + 64; // bit array + object overhead
    }

    // ═══════════════════════════════════════════════════════════════
    //  Optimal Parameter Calculation
    // ═══════════════════════════════════════════════════════════════

    /**
     * Calculates the optimal bit array size (m).
     *
     * <p>Formula: m = -(n × ln(p)) / (ln2)²</p>
     *
     * @param n expected insertions
     * @param p desired false positive probability
     * @return optimal number of bits
     */
    static int calculateOptimalBitSize(long n, double p) {
        if (p == 0) {
            p = Double.MIN_VALUE; // Prevent log(0)
        }
        return (int) Math.ceil(-(n * Math.log(p)) / (Math.log(2) * Math.log(2)));
    }

    /**
     * Calculates the optimal number of hash functions (k).
     *
     * <p>Formula: k = (m / n) × ln2</p>
     *
     * @param m bit array size
     * @param n expected insertions
     * @return optimal number of hash functions
     */
    static int calculateOptimalHashCount(int m, long n) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    // ═══════════════════════════════════════════════════════════════
    //  Validation
    // ═══════════════════════════════════════════════════════════════

    private void validateParameters(long expectedInsertions, double fpp) {
        if (expectedInsertions <= 0) {
            throw new IllegalArgumentException(
                    "Expected insertions must be positive, got: " + expectedInsertions);
        }
        if (fpp <= 0 || fpp >= 1) {
            throw new IllegalArgumentException(
                    "False positive probability must be in (0, 1), got: " + fpp);
        }
        // Check bit size won't overflow int
        long calculatedBitSize = (long) Math.ceil(
                -(expectedInsertions * Math.log(fpp)) / (Math.log(2) * Math.log(2)));
        if (calculatedBitSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Calculated bit size exceeds Integer.MAX_VALUE. " +
                    "Reduce expectedInsertions or increase falsePositiveProbability.");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Getters
    // ═══════════════════════════════════════════════════════════════

    public int getBitSize() {
        return bitSize;
    }

    public int getNumHashFunctions() {
        return numHashFunctions;
    }

    public long getExpectedInsertions() {
        return expectedInsertions;
    }

    public double getConfiguredFalsePositiveProbability() {
        return falsePositiveProbability;
    }

    public long getInsertionCount() {
        return insertionCount.get();
    }
}
