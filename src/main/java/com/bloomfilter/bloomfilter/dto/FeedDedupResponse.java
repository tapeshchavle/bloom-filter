package com.bloomfilter.bloomfilter.dto;

/**
 * Response for feed deduplication — whether to show this post to the user.
 */
public record FeedDedupResponse(
        String userId,
        String postId,
        String compositeKey,
        boolean alreadySeen,
        boolean showInFeed,
        String message
) {
    public static FeedDedupResponse fresh(String userId, String postId, String key) {
        return new FeedDedupResponse(
                userId, postId, key, false, true,
                "User has NOT seen this post — include in feed"
        );
    }

    public static FeedDedupResponse alreadySeen(String userId, String postId, String key) {
        return new FeedDedupResponse(
                userId, postId, key, true, false,
                "User might have already seen this post — skip in feed"
        );
    }
}
