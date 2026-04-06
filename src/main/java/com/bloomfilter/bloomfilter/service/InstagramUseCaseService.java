package com.bloomfilter.bloomfilter.service;

import com.bloomfilter.bloomfilter.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service implementing Instagram-like use cases powered by Bloom Filters.
 *
 * <h3>Use Cases</h3>
 * <ol>
 *   <li><b>Username Availability</b> — instant feedback during signup</li>
 *   <li><b>Duplicate Post Detection</b> — prevent duplicate media uploads</li>
 *   <li><b>Notification Deduplication</b> — prevent notification spam</li>
 *   <li><b>Feed Deduplication</b> — prevent showing the same post twice</li>
 * </ol>
 *
 * <h3>How It Works</h3>
 * <p>Each use case maps to a named Bloom Filter pre-configured in application.yml.
 * The filter is checked first (O(k) time, no I/O). Only when the filter returns
 * "possibly present" do we need to fall back to the database for confirmation.</p>
 */
@Service
public class InstagramUseCaseService {

    private static final Logger log = LoggerFactory.getLogger(InstagramUseCaseService.class);

    // Filter names — must match application.yml keys
    public static final String USERNAME_FILTER = "username-registry";
    public static final String DUPLICATE_POST_FILTER = "duplicate-post-detector";
    public static final String NOTIFICATION_FILTER = "notification-dedup";
    public static final String FEED_FILTER = "feed-dedup";

    private final BloomFilterService bloomFilterService;

    public InstagramUseCaseService(BloomFilterService bloomFilterService) {
        this.bloomFilterService = bloomFilterService;
    }

    // ═══════════════════════════════════════════════════════════════
    //  1. Username Availability Check
    // ═══════════════════════════════════════════════════════════════

    /**
     * Checks if a username is available for registration.
     *
     * <p><b>How Instagram uses this:</b> When a user types a username during signup,
     * every keystroke triggers a Bloom Filter check. If the filter says "not present",
     * the username is definitely available — no need to hit the database. Only when
     * the filter says "possibly present" do we query the users table.</p>
     *
     * <p>This reduces database load by ~99% for username availability checks.</p>
     *
     * @param username the username to check
     * @return availability status with DB lookup recommendation
     */
    public UsernameCheckResponse checkUsername(String username) {
        String normalizedUsername = username.toLowerCase().trim();
        boolean mightExist = bloomFilterService.mightContain(USERNAME_FILTER, normalizedUsername);

        if (mightExist) {
            log.info("Username '{}' — POSSIBLY_TAKEN (recommend DB verification)", normalizedUsername);
            return UsernameCheckResponse.possiblyTaken(normalizedUsername);
        } else {
            log.info("Username '{}' — AVAILABLE (no DB lookup needed)", normalizedUsername);
            return UsernameCheckResponse.available(normalizedUsername);
        }
    }

    /**
     * Registers a username in the Bloom Filter.
     * Called after the username is confirmed and stored in the database.
     */
    public void registerUsername(String username) {
        String normalizedUsername = username.toLowerCase().trim();
        bloomFilterService.add(USERNAME_FILTER, normalizedUsername);
        log.info("Registered username '{}' in Bloom Filter", normalizedUsername);
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. Duplicate Post Detection
    // ═══════════════════════════════════════════════════════════════

    /**
     * Checks if a media file has already been uploaded.
     *
     * <p><b>How Instagram uses this:</b> Before processing an uploaded photo/video,
     * compute a content hash (e.g., SHA-256 of the file). Check the Bloom Filter —
     * if the hash is "not present", it's definitely a new upload. If "possibly present",
     * do a full byte-comparison against existing media.</p>
     *
     * <p>This prevents users from accidentally uploading the same photo twice and
     * saves massive storage costs at Instagram's scale (2B+ users).</p>
     *
     * @param mediaHash hash of the uploaded media content
     * @return duplicate detection result with upload recommendation
     */
    public DuplicatePostResponse checkDuplicatePost(String mediaHash) {
        boolean mightExist = bloomFilterService.mightContain(DUPLICATE_POST_FILTER, mediaHash);

        if (mightExist) {
            log.info("Media hash '{}' — POSSIBLY_DUPLICATE", mediaHash);
            return DuplicatePostResponse.possibleDuplicate(mediaHash);
        } else {
            // Definitely new — add to filter and allow upload
            bloomFilterService.add(DUPLICATE_POST_FILTER, mediaHash);
            log.info("Media hash '{}' — UNIQUE, registered in filter", mediaHash);
            return DuplicatePostResponse.unique(mediaHash);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. Notification Deduplication
    // ═══════════════════════════════════════════════════════════════

    /**
     * Checks if a notification has already been sent for this user+event combination.
     *
     * <p><b>How Instagram uses this:</b> When someone likes your photo, Instagram
     * generates a notification event. Before sending it, the system checks:
     * "Have we already notified user X about event Y?" This prevents spam when
     * multiple systems or retries try to send the same notification.</p>
     *
     * <p>The composite key is {@code userId:eventId} to ensure per-user deduplication.
     * This filter is typically reset daily since notification state is ephemeral.</p>
     *
     * @param userId  the recipient user ID
     * @param eventId the notification event ID
     * @return dedup result with send/suppress recommendation
     */
    public NotificationDedupResponse checkNotificationDedup(String userId, String eventId) {
        String compositeKey = userId + ":" + eventId;
        boolean mightExist = bloomFilterService.mightContain(NOTIFICATION_FILTER, compositeKey);

        if (mightExist) {
            log.info("Notification [{} → {}] — SUPPRESS (already sent)", userId, eventId);
            return NotificationDedupResponse.suppress(userId, eventId, compositeKey);
        } else {
            // Mark as sent
            bloomFilterService.add(NOTIFICATION_FILTER, compositeKey);
            log.info("Notification [{} → {}] — SEND (first time)", userId, eventId);
            return NotificationDedupResponse.send(userId, eventId, compositeKey);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  4. Feed Deduplication
    // ═══════════════════════════════════════════════════════════════

    /**
     * Checks if a user has already seen a specific post in their feed.
     *
     * <p><b>How Instagram uses this:</b> When building a user's feed, the system
     * fetches candidate posts from followed accounts. Before including a post,
     * it checks: "Has user X already seen post Y?" If yes, skip it to keep
     * the feed fresh. This is critical for the infinite scroll experience.</p>
     *
     * <p>The composite key is {@code userId:postId}. At Instagram's scale
     * (500M+ daily active users), storing explicit "seen" lists would require
     * petabytes. A Bloom Filter does this in a fraction of the memory with
     * acceptable false positive rates.</p>
     *
     * @param userId the viewing user ID
     * @param postId the post ID to check
     * @return dedup result with show/skip recommendation
     */
    public FeedDedupResponse checkFeedDedup(String userId, String postId) {
        String compositeKey = userId + ":" + postId;
        boolean mightHaveSeen = bloomFilterService.mightContain(FEED_FILTER, compositeKey);

        if (mightHaveSeen) {
            log.debug("Feed [{} × {}] — SKIP (already seen)", userId, postId);
            return FeedDedupResponse.alreadySeen(userId, postId, compositeKey);
        } else {
            log.debug("Feed [{} × {}] — SHOW (new content)", userId, postId);
            return FeedDedupResponse.fresh(userId, postId, compositeKey);
        }
    }

    /**
     * Marks a post as seen by a user.
     * Called after the post is rendered in the user's feed.
     */
    public void markPostAsSeen(String userId, String postId) {
        String compositeKey = userId + ":" + postId;
        bloomFilterService.add(FEED_FILTER, compositeKey);
        log.debug("Marked post '{}' as seen by user '{}'", postId, userId);
    }
}
