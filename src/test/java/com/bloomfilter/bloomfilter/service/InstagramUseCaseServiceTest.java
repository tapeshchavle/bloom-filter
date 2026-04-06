package com.bloomfilter.bloomfilter.service;

import com.bloomfilter.bloomfilter.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Instagram-like use cases.
 */
class InstagramUseCaseServiceTest {

    private BloomFilterService bloomFilterService;
    private InstagramUseCaseService instagramService;

    @BeforeEach
    void setUp() {
        bloomFilterService = new BloomFilterService();

        // Create the required filters (normally done by auto-configuration)
        bloomFilterService.createFilter("username-registry", 1_000_000, 0.001);
        bloomFilterService.createFilter("duplicate-post-detector", 1_000_000, 0.01);
        bloomFilterService.createFilter("notification-dedup", 1_000_000, 0.01);
        bloomFilterService.createFilter("feed-dedup", 1_000_000, 0.01);

        instagramService = new InstagramUseCaseService(bloomFilterService);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Username Availability
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Username Availability")
    class UsernameAvailability {

        @Test
        @DisplayName("New username should be AVAILABLE")
        void newUsernameIsAvailable() {
            UsernameCheckResponse response = instagramService.checkUsername("john_doe_2024");
            assertEquals("AVAILABLE", response.status());
            assertFalse(response.shouldQueryDatabase());
        }

        @Test
        @DisplayName("Registered username should be POSSIBLY_TAKEN")
        void registeredUsernameIsPossiblyTaken() {
            instagramService.registerUsername("jane_doe");
            UsernameCheckResponse response = instagramService.checkUsername("jane_doe");
            assertEquals("POSSIBLY_TAKEN", response.status());
            assertTrue(response.shouldQueryDatabase());
        }

        @Test
        @DisplayName("Username check should be case-insensitive")
        void caseInsensitive() {
            instagramService.registerUsername("CamelCase_User");
            UsernameCheckResponse response = instagramService.checkUsername("camelcase_user");
            assertEquals("POSSIBLY_TAKEN", response.status());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Duplicate Post Detection
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Duplicate Post Detection")
    class DuplicatePostDetection {

        @Test
        @DisplayName("New media hash should be UNIQUE")
        void newMediaIsUnique() {
            DuplicatePostResponse response = instagramService.checkDuplicatePost("abc123hash");
            assertEquals("UNIQUE", response.status());
            assertTrue(response.proceedWithUpload());
        }

        @Test
        @DisplayName("Same media hash uploaded twice should be POSSIBLY_DUPLICATE")
        void duplicateMediaDetected() {
            instagramService.checkDuplicatePost("same_hash_xyz");
            DuplicatePostResponse response = instagramService.checkDuplicatePost("same_hash_xyz");
            assertEquals("POSSIBLY_DUPLICATE", response.status());
            assertFalse(response.proceedWithUpload());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Notification Deduplication
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Notification Deduplication")
    class NotificationDedup {

        @Test
        @DisplayName("First notification should be sent")
        void firstNotificationSent() {
            NotificationDedupResponse response =
                    instagramService.checkNotificationDedup("user1", "like_event_123");
            assertTrue(response.shouldSendNotification());
        }

        @Test
        @DisplayName("Duplicate notification should be suppressed")
        void duplicateNotificationSuppressed() {
            instagramService.checkNotificationDedup("user1", "like_event_123");
            NotificationDedupResponse response =
                    instagramService.checkNotificationDedup("user1", "like_event_123");
            assertFalse(response.shouldSendNotification());
        }

        @Test
        @DisplayName("Same event for different users should both be sent")
        void differentUsersSameEvent() {
            NotificationDedupResponse r1 =
                    instagramService.checkNotificationDedup("user1", "event_456");
            NotificationDedupResponse r2 =
                    instagramService.checkNotificationDedup("user2", "event_456");

            assertTrue(r1.shouldSendNotification());
            assertTrue(r2.shouldSendNotification());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Feed Deduplication
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Feed Deduplication")
    class FeedDedup {

        @Test
        @DisplayName("Unseen post should be shown")
        void unseenPostShown() {
            FeedDedupResponse response = instagramService.checkFeedDedup("user1", "post_100");
            assertFalse(response.alreadySeen());
            assertTrue(response.showInFeed());
        }

        @Test
        @DisplayName("Seen post should be skipped")
        void seenPostSkipped() {
            instagramService.markPostAsSeen("user1", "post_100");
            FeedDedupResponse response = instagramService.checkFeedDedup("user1", "post_100");
            assertTrue(response.alreadySeen());
            assertFalse(response.showInFeed());
        }

        @Test
        @DisplayName("Same post for different users should both be shown")
        void differentUsersSamePost() {
            FeedDedupResponse r1 = instagramService.checkFeedDedup("user1", "post_200");
            FeedDedupResponse r2 = instagramService.checkFeedDedup("user2", "post_200");

            assertTrue(r1.showInFeed());
            assertTrue(r2.showInFeed());
        }
    }
}
