package com.bloomfilter.bloomfilter.dto;

/**
 * Detailed statistics for a Bloom Filter instance.
 */
public record BloomFilterStatsResponse(
        String filterName,
        long expectedInsertions,
        long actualInsertions,
        int bitArraySize,
        int numHashFunctions,
        int bitsSet,
        double configuredFpp,
        double estimatedFpp,
        double saturationPercent,
        String memoryUsage,
        String healthStatus
) {
    public static BloomFilterStatsResponse from(
            String filterName,
            long expectedInsertions,
            long actualInsertions,
            int bitArraySize,
            int numHashFunctions,
            int bitsSet,
            double configuredFpp,
            double estimatedFpp,
            double saturationRatio,
            long memoryBytes
    ) {
        double saturationPercent = Math.round(saturationRatio * 10000.0) / 100.0;
        String memoryUsage = formatMemory(memoryBytes);
        String health;
        if (saturationRatio < 0.5) {
            health = "🟢 HEALTHY — filter is operating within optimal range";
        } else if (saturationRatio < 0.8) {
            health = "🟡 WARNING — filter is moderately saturated, FPP may be elevated";
        } else {
            health = "🔴 CRITICAL — filter is oversaturated, consider resetting or increasing capacity";
        }

        return new BloomFilterStatsResponse(
                filterName, expectedInsertions, actualInsertions,
                bitArraySize, numHashFunctions, bitsSet,
                configuredFpp,
                Math.round(estimatedFpp * 1_000_000.0) / 1_000_000.0,
                saturationPercent, memoryUsage, health
        );
    }

    private static String formatMemory(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
