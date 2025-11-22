// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.gcp.pubsub.deployment

import app.accrescent.quarkus.gcp.pubsub.PubSubHelper
import io.quarkus.arc.deployment.AdditionalBeanBuildItem
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.FeatureBuildItem

private const val FEATURE_NAME = "google-cloud-pubsub"

class PubSubProcessor {
    @BuildStep
    fun feature(): FeatureBuildItem {
        return FeatureBuildItem(FEATURE_NAME)
    }

    @BuildStep
    fun pubSubHelper(): AdditionalBeanBuildItem {
        return AdditionalBeanBuildItem(PubSubHelper::class.java)
    }
}
