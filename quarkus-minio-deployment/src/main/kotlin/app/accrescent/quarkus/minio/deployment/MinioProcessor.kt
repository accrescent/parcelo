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

    @BuildStep
    fun addDependencyIndexes(items: BuildProducer<IndexDependencyBuildItem>) {
        items.produce(
            listOf(
                IndexDependencyBuildItem("io.minio", "minio"),
                IndexDependencyBuildItem("com.carrotsearch.thirdparty", "simple-xml-safe"),
            )
        )
    }

    @BuildStep
    fun addRuntimeInitializedItem(): RuntimeInitializedClassBuildItem {
        return RuntimeInitializedClassBuildItem(UploadSnowballObjectsArgs::class.qualifiedName)
    }

    @BuildStep
    fun registerForReflection(index: CombinedIndexBuildItem): ReflectiveClassBuildItem {
        val minioArgClasses = index
            .index
            .getAllKnownSubclasses(DotName.createSimple(BaseArgs::class.java.name))
            .map { it.name().toString() }
        val minioMessageClasses = index
            .index
            .getClassesInPackage("io.minio.messages")
            .map { it.name().toString() }
        val simpleXmlClasses = index
            .index
            .getClassesInPackage("org.simpleframework.xml.core")
            .map { it.name().toString() }

        val classes = mutableListOf<String>()
        classes.addAll(minioArgClasses)
        classes.addAll(minioMessageClasses)
        classes.addAll(simpleXmlClasses)

        val item = ReflectiveClassBuildItem
            .builder(*classes.toTypedArray())
            .constructors(true)
            .methods(true)
            .fields(true)
            .build()

        return item
    }
}
