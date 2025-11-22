// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.gcs.devservices

import app.accrescent.quarkus.gcp.pubsub.spi.PubSubConnectionItem
import com.google.cloud.NoCredentials
import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.StorageOptions
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.DevServicesResultBuildItem
import io.quarkus.devservices.common.ConfigureUtil
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.util.Optional
import java.util.OptionalInt
import kotlin.jvm.optionals.getOrElse
import kotlin.jvm.optionals.getOrNull

private const val CONFIG_PREFIX = "quarkus.google.cloud.storage"
private const val DEV_SERVICE_NAME = "google-cloud-storage-server"
private const val DOCKER_IMAGE_NAME = "fsouza/fake-gcs-server"
private const val FAKE_GCS_SERVER_PORT = 4443
private const val FEATURE = "google-cloud-storage-devservices"
private const val PROJECT_ID = "dev-service-project"

class GcsDevServicesProcessor {
    @BuildStep
    fun startContainer(
        config: GcsDevServicesConfig,
        pubSubConnectionItem: Optional<PubSubConnectionItem>,
    ): DevServicesResultBuildItem? {
        if (!config.enabled()) {
            return null
        }

        val container = QuarkusGcsContainer(
            config.imageName(),
            config.port(),
            config.notifications(),
            pubSubConnectionItem,
        )
        container.start()

        val host = "http://${container.host}:${container.firstMappedPort}"
        val devServiceConfig = mapOf("$CONFIG_PREFIX.host" to host)

        val buckets = config.buckets().getOrElse { emptyList() }
        if (buckets.isNotEmpty()) {
            val storage = StorageOptions
                .newBuilder()
                .setCredentials(NoCredentials.getInstance())
                .setHost(host)
                .setProjectId(PROJECT_ID)
                .build()
                .service

            for (bucketName in buckets) {
                storage.create(BucketInfo.of(bucketName))
            }
        }

        val devService = DevServicesResultBuildItem
            .discovered()
            .name(FEATURE)
            .description("Fake GCS server Dev Service")
            .config(devServiceConfig)
            .build()

        return devService
    }
}

private class QuarkusGcsContainer(
    imageName: Optional<String>,
    private val fixedExposedPort: OptionalInt,
    private val notificationsConfig: Optional<NotificationsConfig>,
    private val pubSubConnectionItem: Optional<PubSubConnectionItem>,
) : GenericContainer<QuarkusGcsContainer>(
    DockerImageName
        .parse(imageName.orElseGet { ConfigureUtil.getDefaultImageNameFor(DEV_SERVICE_NAME) })
        .asCompatibleSubstituteFor(DOCKER_IMAGE_NAME)
) {
    override fun configure() {
        super.configure()

        if (fixedExposedPort.isPresent) {
            addFixedExposedPort(fixedExposedPort.asInt, FAKE_GCS_SERVER_PORT)
        } else {
            addExposedPort(FAKE_GCS_SERVER_PORT)
        }

        val notificationsConfig = notificationsConfig.getOrNull()
        if (notificationsConfig == null) {
            withCommand("-scheme", "http")
        } else {
            withCommand(
                "-scheme",
                "http",
                "-event.pubsub-project-id",
                notificationsConfig.pubsubProjectId(),
                "-event.pubsub-topic",
                notificationsConfig.pubsubTopic(),
            )
        }
        pubSubConnectionItem.ifPresent { pubsub ->
            withNetwork(pubsub.network)
            withEnv("PUBSUB_EMULATOR_HOST", "${pubsub.internalHost}:${pubsub.internalPort}")
        }
    }
}
