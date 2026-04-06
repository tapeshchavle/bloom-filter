package com.bloomfilter.bloomfilter.dto;

/**
 * Response DTO for Bloom Filter membership checks.
 */
public record BloomFilterResponse(
        String filterName,
        String element,
        boolean mightContain,
        String verdict
) {
    public static BloomFilterResponse of(String filterName, String element, boolean mightContain) {
        String verdict = mightContain
                ? "POSSIBLY_PRESENT — element might exist (false positive possible)"
                : "DEFINITELY_ABSENT — element is guaranteed not to exist";
        return new BloomFilterResponse(filterName, element, mightContain, verdict);
    }
}
