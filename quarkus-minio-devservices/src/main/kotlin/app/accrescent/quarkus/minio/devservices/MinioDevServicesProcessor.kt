// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.minio.devservices

import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.quarkus.deployment.IsDevServicesSupportedByLaunchMode
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.annotations.BuildSteps
import io.quarkus.deployment.builditem.DevServicesResultBuildItem
import io.quarkus.deployment.dev.devservices.DevServicesConfig
import io.quarkus.devservices.common.ConfigureUtil
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.utility.DockerImageName
import java.util.Optional
import java.util.OptionalInt
import kotlin.jvm.optionals.getOrElse

private const val CONFIG_PREFIX = "quarkus.minio"
private const val DEFAULT_ACCESS_KEY = "minioadmin"
private const val DEFAULT_SECRET_KEY = "minioadmin"
private const val DEV_SERVICE_NAME = "minio"
private const val DOCKER_IMAGE_NAME = "minio/minio"
private const val FEATURE = "quarkus-minio-devservices"
private const val MINIO_API_PORT = 9000
private const val MINIO_CONSOLE_PORT = 9001

@BuildSteps(onlyIf = [IsDevServicesSupportedByLaunchMode::class, DevServicesConfig.Enabled::class])
class MinioDevServicesProcessor {
    @BuildStep
    fun startContainer(config: MinioDevServicesConfig): DevServicesResultBuildItem? {
        if (!config.enabled()) {
            return null
        }

        val container = QuarkusMinioContainer(
            config.imageName(),
            config.apiPort(),
            config.consolePort(),
        )
        container.start()

        val host = "http://${container.host}:${container.firstMappedPort}"
        val devServiceConfig = mapOf(
            "$CONFIG_PREFIX.host" to host,
            "$CONFIG_PREFIX.access-key" to DEFAULT_ACCESS_KEY,
            "$CONFIG_PREFIX.secret-key" to DEFAULT_SECRET_KEY,
        )

        val buckets = config.buckets().getOrElse { emptyList() }
        if (buckets.isNotEmpty()) {
            val client = MinioClient
                .builder()
                .credentials(DEFAULT_ACCESS_KEY, DEFAULT_SECRET_KEY)
                .endpoint(host)
                .build()

            for (bucket in buckets) {
                val requestArgs = MakeBucketArgs.builder().bucket(bucket).build()
                client.makeBucket(requestArgs)
            }
        }

        val devService = DevServicesResultBuildItem
            .discovered()
            .name(FEATURE)
            .description("MinIO Dev Service")
            .config(devServiceConfig)
            .build()

        return devService
    }
}

private class QuarkusMinioContainer(
    imageName: Optional<String>,
    private val apiPort: OptionalInt,
    private val consolePort: OptionalInt,
) : MinIOContainer(
    DockerImageName
        .parse(imageName.orElseGet { ConfigureUtil.getDefaultImageNameFor(DEV_SERVICE_NAME) })
        .asCompatibleSubstituteFor(DOCKER_IMAGE_NAME)
) {
    override fun configure() {
        super.configure()

        if (apiPort.isPresent) {
            addFixedExposedPort(apiPort.asInt, MINIO_API_PORT)
        } else {
            addExposedPort(MINIO_API_PORT)
        }
        if (consolePort.isPresent) {
            addFixedExposedPort(consolePort.asInt, MINIO_CONSOLE_PORT)
        } else {
            addExposedPort(MINIO_CONSOLE_PORT)
        }
    }
}
