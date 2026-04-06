package com.bloomfilter.bloomfilter.dto;

/**
 * Response for notification deduplication.
 */
public record NotificationDedupResponse(
        String userId,
        String eventId,
        String compositeKey,
        boolean shouldSendNotification,
        String message
) {
    public static NotificationDedupResponse send(String userId, String eventId, String key) {
        return new NotificationDedupResponse(
                userId, eventId, key, true,
                "Notification has NOT been sent before — safe to deliver"
        );
    }

    public static NotificationDedupResponse suppress(String userId, String eventId, String key) {
        return new NotificationDedupResponse(
                userId, eventId, key, false,
                "Notification might have already been sent — suppress to avoid spam"
        );
    }
}
