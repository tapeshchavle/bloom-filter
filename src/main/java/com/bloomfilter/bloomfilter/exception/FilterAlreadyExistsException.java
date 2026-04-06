package com.bloomfilter.bloomfilter.exception;

/**
 * Thrown when attempting to create a filter that already exists.
 */
public class FilterAlreadyExistsException extends BloomFilterException {

    private final String filterName;

    public FilterAlreadyExistsException(String filterName) {
        super("Bloom Filter already exists: '" + filterName + "'");
        this.filterName = filterName;
    }

    public String getFilterName() {
        return filterName;
    }
}
