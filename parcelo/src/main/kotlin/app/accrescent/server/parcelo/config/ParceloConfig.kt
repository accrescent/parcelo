// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.config

import io.smallrye.config.ConfigMapping
import io.smallrye.config.WithDefault
import io.smallrye.config.WithName
import jakarta.validation.constraints.NotEmpty
import java.time.Duration
import java.util.Optional

@ConfigMapping(prefix = "parcelo")
interface ParceloConfig {
    fun admin(): Admin
    fun authRedirectUrl(): String
    fun buckets(): Buckets
    fun artifactsBaseUrl(): String
    fun fileProcessingDirectory(): String
    fun objectStorageNotifications(): ObjectStorageNotifications
    fun rateLimiting(): RateLimiting
    fun userRegistration(): Optional<UserRegistration>

    interface Admin {
        fun oidcProvider(): OidcProvider
        fun oidcSubject(): String
    }

    interface Buckets {
        fun appPackage(): String
        fun appUpload(): String
        fun draftListingIconUpload(): String
        fun editUpload(): String
        fun editListingIconUpload(): String
        fun listingImage(): String
        fun publishedArtifact(): String
    }

    interface UserRegistration {
        fun limit(): Limit

        interface Limit {
            fun period(): Duration
            fun registrations(): Long
        }
    }

    enum class IpSource {
        CONNECTION,
        CLOUDFLARE,
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
        fun appEditListingIconUploads(): ObjectStorageNotification
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

    interface RateLimitBuckets {
        fun authenticated(): RateLimitBucket
        fun unauthenticated(): RateLimitBucket
        fun uploadApis(): RateLimitBucket
    }

    interface RateLimiting {
        @WithDefault("connection")
        fun ipSource(): IpSource
        fun buckets(): RateLimitBuckets
    }
}
