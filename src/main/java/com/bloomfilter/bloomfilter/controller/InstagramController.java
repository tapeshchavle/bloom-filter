package com.bloomfilter.bloomfilter.controller;

import com.bloomfilter.bloomfilter.dto.*;
import com.bloomfilter.bloomfilter.service.InstagramUseCaseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller demonstrating Instagram-like Bloom Filter use cases.
 *
 * <p>These endpoints showcase how Instagram uses Bloom Filters at scale
 * to solve real-world problems with billions of users.</p>
 *
 * <h3>Endpoints Summary</h3>
 * <ul>
 *   <li>{@code POST /api/v1/instagram/username/check} — Check username availability</li>
 *   <li>{@code POST /api/v1/instagram/username/register} — Register a username</li>
 *   <li>{@code POST /api/v1/instagram/post/duplicate-check} — Detect duplicate uploads</li>
 *   <li>{@code POST /api/v1/instagram/notification/dedup} — Deduplicate notifications</li>
 *   <li>{@code POST /api/v1/instagram/feed/check} — Check feed deduplication</li>
 *   <li>{@code POST /api/v1/instagram/feed/mark-seen} — Mark post as seen</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/instagram")
public class InstagramController {

    private final InstagramUseCaseService instagramService;

    public InstagramController(InstagramUseCaseService instagramService) {
        this.instagramService = instagramService;
    }

    // ═══════════════════════════════════════════════════════════════
    //  1. Username Availability
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if a username is available for registration.
     *
     * <p>This is what happens when you type a username during Instagram signup
     * and see the ✅ or ❌ indicator in real-time. The Bloom Filter provides
     * instant feedback without hitting the database.</p>
     *
     * <p>If status = AVAILABLE → username is guaranteed to be free.<br>
     * If status = POSSIBLY_TAKEN → need to verify with the database.</p>
     */
    @PostMapping("/username/check")
    public ResponseEntity<UsernameCheckResponse> checkUsername(
            @Valid @RequestBody UsernameCheckRequest request) {
        return ResponseEntity.ok(instagramService.checkUsername(request.username()));
    }

    /**
     * Register a username after successful database insert.
     * This adds the username to the Bloom Filter so future checks are instant.
     */
    @PostMapping("/username/register")
    public ResponseEntity<UsernameCheckResponse> registerUsername(
            @Valid @RequestBody UsernameCheckRequest request) {
        instagramService.registerUsername(request.username());
        return ResponseEntity.ok(UsernameCheckResponse.possiblyTaken(request.username().toLowerCase()));
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. Duplicate Post Detection
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if a media file has already been uploaded.
     *
     * <p>Before processing an uploaded photo, compute its content hash (SHA-256)
     * and check against the Bloom Filter. If the hash is new, the upload proceeds
     * and the hash is automatically registered. If possibly duplicate, the system
     * should perform a full byte-comparison before rejecting.</p>
     */
    @PostMapping("/post/duplicate-check")
    public ResponseEntity<DuplicatePostResponse> checkDuplicatePost(
            @Valid @RequestBody DuplicatePostRequest request) {
        return ResponseEntity.ok(instagramService.checkDuplicatePost(request.mediaHash()));
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. Notification Deduplication
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check and deduplicate a notification.
     *
     * <p>Before sending a push notification (e.g., "john_doe liked your photo"),
     * check if we've already notified this user about this event. If not,
     * the notification is marked as sent and should be delivered.</p>
     *
     * <p>This prevents notification spam when multiple retries or redundant
     * systems try to send the same notification.</p>
     */
    @PostMapping("/notification/dedup")
    public ResponseEntity<NotificationDedupResponse> checkNotificationDedup(
            @Valid @RequestBody NotificationDedupRequest request) {
        return ResponseEntity.ok(
                instagramService.checkNotificationDedup(request.userId(), request.eventId())
        );
    }

    // ═══════════════════════════════════════════════════════════════
    //  4. Feed Deduplication
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if a user has already seen a specific post.
     *
     * <p>Used during feed generation to filter out posts the user has already
     * scrolled past, ensuring the infinite scroll always shows fresh content.</p>
     */
    @PostMapping("/feed/check")
    public ResponseEntity<FeedDedupResponse> checkFeedDedup(
            @Valid @RequestBody FeedDedupRequest request) {
        return ResponseEntity.ok(
                instagramService.checkFeedDedup(request.userId(), request.postId())
        );
    }

    /**
     * Mark a post as seen by a user.
     *
     * <p>Called after a post is rendered in the user's feed viewport.
     * Subsequent feed generation will skip this post.</p>
     */
    @PostMapping("/feed/mark-seen")
    public ResponseEntity<FeedDedupResponse> markPostAsSeen(
            @Valid @RequestBody FeedDedupRequest request) {
        instagramService.markPostAsSeen(request.userId(), request.postId());
        return ResponseEntity.ok(
                FeedDedupResponse.alreadySeen(request.userId(), request.postId(),
                        request.userId() + ":" + request.postId())
        );
    }
}
