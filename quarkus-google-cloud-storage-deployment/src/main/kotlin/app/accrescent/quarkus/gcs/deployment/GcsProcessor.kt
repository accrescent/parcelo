// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.gcs.deployment

import app.accrescent.quarkus.gcs.StorageProducer
import io.quarkus.arc.deployment.AdditionalBeanBuildItem
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.FeatureBuildItem

private const val FEATURE_NAME = "google-cloud-storage"

class GcsProcessor {
    @BuildStep
    fun feature(): FeatureBuildItem {
        return FeatureBuildItem(FEATURE_NAME)
    }

    @BuildStep
    fun storageProducer(): AdditionalBeanBuildItem {
        return AdditionalBeanBuildItem(StorageProducer::class.java)
    }
}
