package com.bloomfilter.bloomfilter.dto;

/**
 * Response for Instagram username availability check.
 */
public record UsernameCheckResponse(
        String username,
        String status,
        String message,
        boolean shouldQueryDatabase
) {
    public static UsernameCheckResponse available(String username) {
        return new UsernameCheckResponse(
                username,
                "AVAILABLE",
                "Username '" + username + "' is definitely available — no DB lookup needed",
                false
        );
    }

    public static UsernameCheckResponse possiblyTaken(String username) {
        return new UsernameCheckResponse(
                username,
                "POSSIBLY_TAKEN",
                "Username '" + username + "' might already be taken — verify with database",
                true
        );
    }
}
