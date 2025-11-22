// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.gcp.pubsub.spi

import io.quarkus.builder.item.SimpleBuildItem
import org.testcontainers.containers.Network

data class PubSubConnectionItem(
    val network: Network,
    val internalHost: String,
    val internalPort: Int,
) : SimpleBuildItem()
