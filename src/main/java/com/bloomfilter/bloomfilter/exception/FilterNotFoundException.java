package com.bloomfilter.bloomfilter.exception;

/**
 * Thrown when a requested Bloom Filter does not exist in the registry.
 */
public class FilterNotFoundException extends BloomFilterException {

    private final String filterName;

    public FilterNotFoundException(String filterName) {
        super("Bloom Filter not found: '" + filterName + "'");
        this.filterName = filterName;
    }

    public String getFilterName() {
        return filterName;
    }
}
