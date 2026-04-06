package com.bloomfilter.bloomfilter.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to dynamically create a new Bloom Filter at runtime.
 */
public record CreateFilterRequest(
        @NotBlank(message = "Filter name must not be blank")
        String filterName,

        @Min(value = 1, message = "Expected insertions must be at least 1")
        long expectedInsertions,

        @DecimalMin(value = "0.0001", message = "FPP must be at least 0.0001")
        @DecimalMax(value = "0.5", message = "FPP must be at most 0.5")
        double falsePositiveProbability
) {}
