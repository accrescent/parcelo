// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.gcp.pubsub.devservices

import io.quarkus.runtime.annotations.ConfigGroup
import java.util.Optional

@ConfigGroup
interface TopicConfig {
    fun subscriptionNames(): Optional<List<String>>
}
