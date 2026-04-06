package com.bloomfilter.bloomfilter.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for generic Bloom Filter operations.
 */
public record BloomFilterRequest(
        @NotBlank(message = "Element must not be blank")
        String element
) {}
