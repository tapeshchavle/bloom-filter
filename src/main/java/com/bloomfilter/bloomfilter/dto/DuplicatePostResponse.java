package com.bloomfilter.bloomfilter.dto;

/**
 * Response for duplicate post detection.
 */
public record DuplicatePostResponse(
        String mediaHash,
        String status,
        String message,
        boolean proceedWithUpload
) {
    public static DuplicatePostResponse unique(String mediaHash) {
        return new DuplicatePostResponse(
                mediaHash,
                "UNIQUE",
                "Media is definitely new — safe to upload",
                true
        );
    }

    public static DuplicatePostResponse possibleDuplicate(String mediaHash) {
        return new DuplicatePostResponse(
                mediaHash,
                "POSSIBLY_DUPLICATE",
                "Media might already exist — perform full content comparison before uploading",
                false
        );
    }
}
