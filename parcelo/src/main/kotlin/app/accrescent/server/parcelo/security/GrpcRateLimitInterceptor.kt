// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.console.v1alpha1.AppDraftServiceGrpc
import app.accrescent.console.v1alpha1.AppEditServiceGrpc
import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.security.Principal.IpAddress
import app.accrescent.server.parcelo.security.Principal.User
import io.agroal.api.AgroalDataSource
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.TokensInheritanceStrategy
import io.github.bucket4j.distributed.BucketProxy
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper
import io.github.bucket4j.postgresql.Bucket4jPostgreSQL
import io.grpc.Grpc
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import io.quarkus.logging.Log
import io.quarkus.scheduler.Scheduled
import io.vertx.core.Vertx
import io.vertx.grpc.BlockingServerInterceptor
import jakarta.enterprise.context.ApplicationScoped
import java.net.InetSocketAddress
import java.time.Duration

private const val BUCKET_TABLE_NAME = "rate_limit_buckets"
private const val REMOVE_EXPIRED_BUCKET_BATCH_SIZE = 1000
private const val REMOVE_EXPIRED_BUCKET_JOB_PERIOD = "P1D"
private const val REMOVED_EXPIRED_BUCKET_CONTINUE_THRESHOLD = 50

@ApplicationScoped
class GrpcRateLimitInterceptor(
    private val config: ParceloConfig,
    private val dataSource: AgroalDataSource,
    private val vertx: Vertx,
) : ServerInterceptor by BlockingServerInterceptor
    .wrap(vertx, GrpcRateLimitInterceptorImpl(config, dataSource))

private class GrpcRateLimitInterceptorImpl(
    private val config: ParceloConfig,
    dataSource: AgroalDataSource,
) : ServerInterceptor {
    private companion object {
        private val BUCKET_KEEP_AFTER_REFILL_DURATION = Duration.ofSeconds(30)

        private val UPLOAD_APIS_METHODS = setOf(
            AppDraftServiceGrpc.getCreateAppDraftUploadOperationMethod().fullMethodName,
            AppDraftServiceGrpc.getCreateAppDraftListingIconUploadOperationMethod().fullMethodName,
            AppEditServiceGrpc.getCreateAppEditUploadOperationMethod().fullMethodName,
            AppEditServiceGrpc.getCreateAppEditListingIconUploadOperationMethod().fullMethodName,
        )
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

    override fun <ReqT : Any, RespT : Any> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        val userId = AuthnContextKey.USER_ID.get()

        val principal = if (userId == null) {
            val clientAddress = call.attributes.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR) ?: run {
                call.close(Status.INTERNAL.withDescription("no remote address found"), Metadata())
                return object : ServerCall.Listener<ReqT>() {}
            }
            if (clientAddress !is InetSocketAddress) {
                call.close(
                    Status.INTERNAL.withDescription("client address is not an IP address"),
                    Metadata(),
                )
                return object : ServerCall.Listener<ReqT>() {}
            }

            IpAddress(clientAddress)
        } else {
            User(userId)
        }

        val rateLimitConfig = when (principal) {
            is IpAddress -> config.rateLimits().unauthenticated()
            is User -> config.rateLimits().authenticated()
        }
        val userBucket = getBucket(rateLimitConfig, principal.bucketKey())

        // Apply per-user rate limit
        if (!userBucket.tryConsume(1)) {
            call.close(Status.RESOURCE_EXHAUSTED.withDescription("rate limit exceeded"), Metadata())
            object : ServerCall.Listener<ReqT>() {}
        }

        // Apply API-specific rate limits
        val methodId = call.methodDescriptor.fullMethodName
        if (UPLOAD_APIS_METHODS.contains(methodId)) {
            val rateLimitConfig = config.rateLimits().uploadApis()
            val bucketKey = "${principal.bucketKey()}|$methodId"
            val methodBucket = getBucket(rateLimitConfig, bucketKey)

            if (!methodBucket.tryConsume(1)) {
                // Return the previously consumed token to the user bucket since this request is
                // rejected
                userBucket.addTokens(1)

                call.close(
                    Status.RESOURCE_EXHAUSTED.withDescription("rate limit exceeded"),
                    Metadata(),
                )
                object : ServerCall.Listener<ReqT>() {}
            }
        }

        return next.startCall(call, headers)
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
}

private sealed class Principal {
    data class User(val userId: String) : Principal()
    data class IpAddress(val address: InetSocketAddress) : Principal()

    fun bucketKey(): String {
        return when (this) {
            is IpAddress -> "ip|${address.hostString}"
            is User -> "user|$userId"
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
