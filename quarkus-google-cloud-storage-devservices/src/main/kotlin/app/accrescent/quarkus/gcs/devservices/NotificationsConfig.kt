// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.gcs.devservices

import io.smallrye.config.ConfigMapping

@ConfigMapping(prefix = "notifications")
interface NotificationsConfig {
    fun pubsubProjectId(): String

    fun pubsubTopic(): String
}
