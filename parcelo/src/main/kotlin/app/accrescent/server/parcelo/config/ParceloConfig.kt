// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.config

import io.smallrye.config.ConfigMapping
import io.smallrye.config.WithName

@ConfigMapping(prefix = "parcelo")
interface ParceloConfig {
    fun admin(): Admin

    fun appPackageBucket(): String

    fun appUploadBucket(): String

    fun imageUploadServiceBaseUrl(): String

    fun listingImageBucket(): String

    fun packageProcessingDirectory(): String

    fun publishedArtifactBucket(): String

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
}
