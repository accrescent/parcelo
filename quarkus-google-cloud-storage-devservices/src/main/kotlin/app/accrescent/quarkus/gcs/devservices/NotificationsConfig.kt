// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.gcs.devservices

import io.smallrye.config.ConfigMapping
import java.util.Optional

@ConfigMapping(prefix = "notifications")
interface NotificationsConfig {
    fun bucket(): Optional<String>

    fun pubsubProjectId(): String

    fun pubsubTopic(): String
}
