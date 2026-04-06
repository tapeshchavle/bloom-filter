package com.bloomfilter.bloomfilter.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to check for duplicate media uploads.
 */
public record DuplicatePostRequest(
        @NotBlank(message = "Media hash must not be blank")
        String mediaHash
) {}
