package com.bloomfilter.bloomfilter.controller;

import com.bloomfilter.bloomfilter.dto.*;
import com.bloomfilter.bloomfilter.service.BloomFilterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * REST controller for generic Bloom Filter operations.
 *
 * <p>Provides CRUD endpoints to create, query, and manage named Bloom Filters.
 * This is the low-level API — for Instagram-specific use cases, see
 * {@link InstagramController}.</p>
 */
@RestController
@RequestMapping("/api/v1/bloom")
public class BloomFilterController {

    private final BloomFilterService bloomFilterService;

    public BloomFilterController(BloomFilterService bloomFilterService) {
        this.bloomFilterService = bloomFilterService;
    }

    @PostMapping("/filters")
    public ResponseEntity<Map<String, Object>> createFilter(@Valid @RequestBody CreateFilterRequest request) {
        bloomFilterService.createFilter(
                request.filterName(),
                request.expectedInsertions(),
                request.falsePositiveProbability()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Bloom Filter '" + request.filterName() + "' created successfully");
        response.put("filterName", request.filterName());
        response.put("expectedInsertions", request.expectedInsertions());
        response.put("falsePositiveProbability", request.falsePositiveProbability());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists all registered Bloom Filter names.
     */
    @GetMapping("/filters")
    public ResponseEntity<Map<String, Object>> listFilters() {
        Set<String> filterNames = bloomFilterService.getFilterNames();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("count", filterNames.size());
        response.put("filters", filterNames);

        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a Bloom Filter from the registry.
     */
    @DeleteMapping("/filters/{filterName}")
    public ResponseEntity<Map<String, Object>> deleteFilter(@PathVariable String filterName) {
        bloomFilterService.deleteFilter(filterName);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Bloom Filter '" + filterName + "' deleted successfully");

        return ResponseEntity.ok(response);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Core Operations
    // ═══════════════════════════════════════════════════════════════

    /**
     * Adds an element to a Bloom Filter.
     */
    @PostMapping("/{filterName}/add")
    public ResponseEntity<Map<String, Object>> addElement(
            @PathVariable String filterName,
            @Valid @RequestBody BloomFilterRequest request) {

        bloomFilterService.add(filterName, request.element());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("filterName", filterName);
        response.put("element", request.element());
        response.put("message", "Element added to Bloom Filter '" + filterName + "'");

        return ResponseEntity.ok(response);
    }

    /**
     * Checks if an element might exist in a Bloom Filter.
     *
     * <p>Remember: {@code mightContain=false} means <b>DEFINITELY NOT</b> present.
     * {@code mightContain=true} means <b>POSSIBLY</b> present (with configured FPP).</p>
     */
    @PostMapping("/{filterName}/check")
    public ResponseEntity<BloomFilterResponse> checkElement(
            @PathVariable String filterName,
            @Valid @RequestBody BloomFilterRequest request) {

        boolean result = bloomFilterService.mightContain(filterName, request.element());
        return ResponseEntity.ok(BloomFilterResponse.of(filterName, request.element(), result));
    }

    /**
     * Resets a Bloom Filter, clearing all bits.
     */
    @PostMapping("/{filterName}/reset")
    public ResponseEntity<Map<String, Object>> resetFilter(@PathVariable String filterName) {
        bloomFilterService.reset(filterName);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("filterName", filterName);
        response.put("message", "Bloom Filter '" + filterName + "' has been reset");

        return ResponseEntity.ok(response);
    }

    /**
     * Returns detailed statistics for a Bloom Filter.
     */
    @GetMapping("/{filterName}/stats")
    public ResponseEntity<BloomFilterStatsResponse> getStats(@PathVariable String filterName) {
        return ResponseEntity.ok(bloomFilterService.getStats(filterName));
    }
}
