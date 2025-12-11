// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.minio.deployment

import app.accrescent.quarkus.minio.MinioClientProducer
import io.quarkus.arc.deployment.AdditionalBeanBuildItem
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.FeatureBuildItem

private const val FEATURE_NAME = "minio"

class MinioProcessor {
    @BuildStep
    fun feature(): FeatureBuildItem {
        return FeatureBuildItem(FEATURE_NAME)
    }

    @BuildStep
    fun minioClientProducer(): AdditionalBeanBuildItem {
        return AdditionalBeanBuildItem(MinioClientProducer::class.java)
    }
}
