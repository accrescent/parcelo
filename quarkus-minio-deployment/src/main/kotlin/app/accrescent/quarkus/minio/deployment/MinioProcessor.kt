// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.minio.deployment

import app.accrescent.quarkus.minio.MinioClientProducer
import io.minio.BaseArgs
import io.minio.UploadSnowballObjectsArgs
import io.quarkus.arc.deployment.AdditionalBeanBuildItem
import io.quarkus.deployment.annotations.BuildProducer
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.CombinedIndexBuildItem
import io.quarkus.deployment.builditem.FeatureBuildItem
import io.quarkus.deployment.builditem.IndexDependencyBuildItem
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem
import org.jboss.jandex.DotName

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
