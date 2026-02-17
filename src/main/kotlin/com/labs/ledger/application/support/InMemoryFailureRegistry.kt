package com.labs.ledger.application.support

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.labs.ledger.domain.port.FailureRecord
import com.labs.ledger.domain.port.FailureRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * In-memory failure registry using Caffeine cache
 *
 * Features:
 * - Thread-safe concurrent access (Caffeine's internal synchronization)
 * - Automatic TTL-based eviction (prevents memory leaks)
 * - Efficient memory usage with size limits
 * - Monitoring-friendly with size() metric
 *
 * Configuration:
 * - TTL: 1 hour (enough time for DB persistence to complete)
 * - Max size: 10,000 entries (protects against memory exhaustion)
 * - Eviction policy: LRU (Least Recently Used)
 *
 * @property ttl Time to live for cache entries
 * @property maxSize Maximum number of entries
 */
class InMemoryFailureRegistry(
    private val ttl: Duration = Duration.ofHours(1),
    private val maxSize: Long = 10_000
) : FailureRegistry {

    private val cache: Cache<String, FailureRecord> = Caffeine.newBuilder()
        .expireAfterWrite(ttl)
        .maximumSize(maxSize)
        .recordStats()
        .removalListener<String, FailureRecord> { key, _, cause ->
            logger.debug { "Failure record evicted: key=$key, cause=$cause" }
        }
        .build()

    override fun register(idempotencyKey: String, record: FailureRecord) {
        cache.put(idempotencyKey, record)
        logger.debug {
            "Failure registered in memory: key=$idempotencyKey, " +
                "status=${record.transfer.status}, size=${size()}"
        }
    }

    override fun get(idempotencyKey: String): FailureRecord? {
        return cache.getIfPresent(idempotencyKey)?.also {
            logger.debug { "Failure cache hit: key=$idempotencyKey" }
        }
    }

    override fun remove(idempotencyKey: String) {
        cache.invalidate(idempotencyKey)
        logger.debug { "Failure removed from memory: key=$idempotencyKey" }
    }

    override fun size(): Int {
        return cache.estimatedSize().toInt()
    }

    /**
     * Expose internal Caffeine cache for metrics binding (CaffeineCacheMetrics)
     */
    fun getCache(): Cache<String, FailureRecord> = cache

    /**
     * Get cache statistics for monitoring
     */
    fun stats(): CacheStats {
        val stats = cache.stats()
        return CacheStats(
            hitCount = stats.hitCount(),
            missCount = stats.missCount(),
            hitRate = stats.hitRate(),
            evictionCount = stats.evictionCount(),
            currentSize = size()
        )
    }
}

/**
 * Cache statistics for monitoring and observability
 */
data class CacheStats(
    val hitCount: Long,
    val missCount: Long,
    val hitRate: Double,
    val evictionCount: Long,
    val currentSize: Int
)
