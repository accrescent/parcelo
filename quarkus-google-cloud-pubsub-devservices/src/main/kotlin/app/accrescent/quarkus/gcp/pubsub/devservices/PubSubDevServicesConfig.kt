// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.gcp.pubsub.devservices

import io.quarkus.runtime.annotations.ConfigRoot
import io.smallrye.config.ConfigMapping
import io.smallrye.config.WithDefault
import java.util.Optional
import java.util.OptionalInt

@ConfigMapping(prefix = "quarkus.google.cloud.pubsub.devservices")
@ConfigRoot
interface PubSubDevServicesConfig {
    @WithDefault("true")
    fun enabled(): Boolean

    fun imageName(): Optional<String>

    fun port(): OptionalInt

    fun projectId(): Optional<String>

    fun topics(): Map<String, TopicConfig>
}
