// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.util.sha256Hash
import io.agroal.api.AgroalDataSource
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.TokensInheritanceStrategy
import io.github.bucket4j.distributed.BucketProxy
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper
import io.github.bucket4j.postgresql.Bucket4jPostgreSQL
import io.quarkus.logging.Log
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import java.time.Duration

private const val BUCKET_TABLE_NAME = "rate_limit_buckets"
private const val REMOVE_EXPIRED_BUCKET_BATCH_SIZE = 1000
private const val REMOVE_EXPIRED_BUCKET_JOB_PERIOD = "P1D"
private const val REMOVED_EXPIRED_BUCKET_CONTINUE_THRESHOLD = 50
private const val UPLOAD_APIS_BUCKET_SUFFIX = "upload_apis"

@ApplicationScoped
class RateLimitService(
    private val config: ParceloConfig,
    dataSource: AgroalDataSource,
    private val saltService: IpAddressSaltService,
) {
    private companion object {
        private val BUCKET_KEEP_AFTER_REFILL_DURATION = Duration.ofSeconds(30)
    }

    private val proxyManager = Bucket4jPostgreSQL
        .selectForUpdateBasedBuilder(dataSource)
        .expirationAfterWrite(
            ExpirationAfterWriteStrategy
                .basedOnTimeForRefillingBucketUpToMax(BUCKET_KEEP_AFTER_REFILL_DURATION)
        )
        .primaryKeyMapper(PrimaryKeyMapper.STRING)
        .table(BUCKET_TABLE_NAME)
        .build()

    fun tryRequest(principal: Principal, apiCategory: ApiCategory? = null): Boolean {
        val rateLimitConfig = when (principal) {
            is Principal.IpAddress -> config.rateLimiting().buckets().unauthenticated()
            is Principal.User -> config.rateLimiting().buckets().authenticated()
        }
        val principalBucket = getBucket(rateLimitConfig, getBucketKey(principal))

        // Apply per-principal rate limit
        if (!principalBucket.tryConsume(1)) {
            return false
        }

        // Apply API-specific rate limits
        if (apiCategory != null) {
            val apiRateLimitConfig = when (apiCategory) {
                ApiCategory.UPLOAD_APIS -> config.rateLimiting().buckets().uploadApis()
            }
            val bucketKey = "${getBucketKey(principal)}|$UPLOAD_APIS_BUCKET_SUFFIX"
            val apiBucket = getBucket(apiRateLimitConfig, bucketKey)

            if (!apiBucket.tryConsume(1)) {
                // Return the previously consumed token to the principal bucket since this request
                // is rejected
                principalBucket.addTokens(1)

                return false
            }
        }

        return true
    }

    @Scheduled(every = REMOVE_EXPIRED_BUCKET_JOB_PERIOD)
    fun removeExpiredBuckets() {
        Log.info("Attempting to remove expired rate limit buckets")

        var removed: Int
        do {
            removed = proxyManager.removeExpired(REMOVE_EXPIRED_BUCKET_BATCH_SIZE)
            if (removed > 0) {
                Log.info("Removed $removed expired rate limit buckets")
            } else {
                Log.info("There are no expired buckets to remove")
            }
        } while (removed > REMOVED_EXPIRED_BUCKET_CONTINUE_THRESHOLD)
    }

    private fun getBucket(config: ParceloConfig.RateLimitBucket, key: String): BucketProxy {
        return proxyManager
            .builder()
            .withImplicitConfigurationReplacement(
                config.version(),
                TokensInheritanceStrategy.PROPORTIONALLY,
            )
            .build(key) { config.toBucketConfiguration() }
    }

    private fun getBucketKey(principal: Principal): String {
        return when (principal) {
            is Principal.IpAddress -> {
                val addressBytes = principal.address.address
                val salt = saltService.getCurrentSalt()
                val hash = sha256Hash(addressBytes + salt)

                "ip|$hash"
            }

            is Principal.User -> "user|${principal.userId}"
        }
    }
}

private fun ParceloConfig.RateLimitBucket.toBucketConfiguration(): BucketConfiguration {
    val configBuilder = BucketConfiguration.builder()
    for ((id, limit) in this.limits()) {
        configBuilder.addLimit {
            it
                .capacity(limit.requests())
                .refillGreedy(limit.requests(), limit.period())
                .id(id)
        }
    }

    return configBuilder.build()
}
