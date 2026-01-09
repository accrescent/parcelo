// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.config

import io.smallrye.config.ConfigMapping
import io.smallrye.config.WithName
import jakarta.validation.constraints.NotEmpty
import java.time.Duration

@ConfigMapping(prefix = "parcelo")
interface ParceloConfig {
    fun admin(): Admin

    fun appPackageBucket(): String

    fun appUploadBucket(): String

    fun artifactsBaseUrl(): String

    fun imageUploadServiceBaseUrl(): String

    fun listingImageBucket(): String

    fun packageProcessingDirectory(): String

    fun publishedArtifactBucket(): String

    fun rateLimits(): RateLimits

    @WithName("pubsub")
    fun pubSub(): PubSub

    interface Admin {
        fun identityProvider(): String

        fun scopedUserId(): String
    }

    interface PubSub {
        fun projectId(): String
        fun subscriptionName(): String
    }

    interface RateLimit {
        fun period(): Duration
        fun requests(): Long
    }

    interface RateLimitBucket {
        fun version(): Long

        @NotEmpty
        fun limits(): Map<String, RateLimit>
    }

    interface RateLimits {
        fun authenticated(): RateLimitBucket

        fun unauthenticated(): RateLimitBucket

        fun uploadApis(): RateLimitBucket
    }
}
