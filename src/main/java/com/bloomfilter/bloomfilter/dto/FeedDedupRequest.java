package com.bloomfilter.bloomfilter.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to check feed deduplication — has user already seen this post?
 */
public record FeedDedupRequest(
        @NotBlank(message = "User ID must not be blank")
        String userId,

        @NotBlank(message = "Post ID must not be blank")
        String postId
) {}
