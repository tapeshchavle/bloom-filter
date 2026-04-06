package com.bloomfilter.bloomfilter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to check Instagram username availability.
 */
public record UsernameCheckRequest(
        @NotBlank(message = "Username must not be blank")
        @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
        String username
) {}
