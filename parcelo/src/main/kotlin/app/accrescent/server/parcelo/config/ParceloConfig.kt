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

    fun buckets(): Buckets

    fun artifactsBaseUrl(): String

    fun fileProcessingDirectory(): String

    fun objectStorageNotifications(): ObjectStorageNotifications

    fun rateLimits(): RateLimits

    interface Admin {
        fun oidcProvider(): OidcProvider

        fun oidcSubject(): String
    }

    interface Buckets {
        fun appPackage(): String
        fun appUpload(): String
        fun draftListingIconUpload(): String
        fun editUpload(): String
        fun listingImage(): String
        fun publishedArtifact(): String
    }

    interface ObjectStorageNotification {
        @WithName("pubsub-project-id")
        fun pubSubProjectId(): String

        @WithName("pubsub-subscription-name")
        fun pubSubSubscriptionName(): String
    }

    interface ObjectStorageNotifications {
        fun appDraftUploads(): ObjectStorageNotification
        fun appDraftListingIconUploads(): ObjectStorageNotification
        fun appEditUploads(): ObjectStorageNotification
    }

    enum class OidcProvider {
        LOCAL,
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
