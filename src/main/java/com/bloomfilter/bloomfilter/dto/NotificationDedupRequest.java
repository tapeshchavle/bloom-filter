package com.bloomfilter.bloomfilter.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to check notification deduplication.
 */
public record NotificationDedupRequest(
        @NotBlank(message = "User ID must not be blank")
        String userId,

        @NotBlank(message = "Event ID must not be blank")
        String eventId
) {}
