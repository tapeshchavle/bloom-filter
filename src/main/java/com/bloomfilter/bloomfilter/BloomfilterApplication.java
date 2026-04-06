package com.bloomfilter.bloomfilter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Production-grade Bloom Filter Service for Instagram-like applications.
 *
 * <p>This application provides:</p>
 * <ul>
 *   <li>A Bloom Filter engine built entirely from scratch (Murmur3 + double hashing)</li>
 *   <li>REST APIs for generic Bloom Filter operations</li>
 *   <li>Instagram-specific use cases: username availability, duplicate post detection,
 *       notification dedup, feed dedup</li>
 *   <li>Actuator health checks and Prometheus metrics for observability</li>
 * </ul>
 *
 * @see <a href="http://localhost:8080/actuator/health">Health Check</a>
 * @see <a href="http://localhost:8080/actuator/prometheus">Prometheus Metrics</a>
 */
@SpringBootApplication
@EnableScheduling
public class BloomfilterApplication {

	public static void main(String[] args) {
		SpringApplication.run(BloomfilterApplication.class, args);
	}

}
