// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.gcs.devservices

import app.accrescent.quarkus.gcp.pubsub.spi.PubSubConnectionItem
import com.google.cloud.NoCredentials
import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.StorageOptions
import io.quarkus.deployment.IsDevServicesSupportedByLaunchMode
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.annotations.BuildSteps
import io.quarkus.deployment.builditem.DevServicesResultBuildItem
import io.quarkus.deployment.dev.devservices.DevServicesConfig
import io.quarkus.devservices.common.ConfigureUtil
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.net.ServerSocket
import java.util.Optional
import kotlin.jvm.optionals.getOrElse

private const val CONFIG_PREFIX = "quarkus.google.cloud.storage"
private const val DEV_SERVICE_NAME = "google-cloud-storage-server"
private const val DOCKER_IMAGE_NAME = "fsouza/fake-gcs-server"
private const val FAKE_GCS_SERVER_PORT = 4443
private const val FEATURE = "google-cloud-storage-devservices"
private const val PROJECT_ID = "dev-service-project"

@BuildSteps(onlyIf = [IsDevServicesSupportedByLaunchMode::class, DevServicesConfig.Enabled::class])
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
    private val notificationConfigs: List<NotificationsConfig>,
    private val pubSubConnectionItem: Optional<PubSubConnectionItem>,
) : GenericContainer<QuarkusGcsContainer>(
    DockerImageName
        .parse(imageName.orElseGet { ConfigureUtil.getDefaultImageNameFor(DEV_SERVICE_NAME) })
        .asCompatibleSubstituteFor(DOCKER_IMAGE_NAME)
) {
    override fun configure() {
        super.configure()

        withExposedPorts(FAKE_GCS_SERVER_PORT)

        // We must expose a "fixed" port to work around
        // https://github.com/fsouza/fake-gcs-server/issues/1624 (See
        // https://github.com/fsouza/fake-gcs-server/issues/1624#issuecomment-3667735362 for more
        // details), but using a constant port value could cause port conflicts, especially when
        // starting new container instances in Quarkus dev mode.
        //
        // To fix this problem, "reserve" a port that is not in use on the system by binding to it,
        // then immediately unbind and use that port for our port mapping. This approach is
        // technically racy because another application on the host could bind to the "reserved"
        // port after we unbind it and before our container binds to it, but it is less likely to
        // cause conflicts than using a constant fixed port and an error caused by a binding race
        // can be trivially worked around by restarting the Dev Service anyway, so it's not a
        // significant issue for us.
        val reservedPort = ServerSocket(0).use { it.localPort }
        addFixedExposedPort(reservedPort, FAKE_GCS_SERVER_PORT)

        val commandFlags = mutableListOf("-scheme", "http", "-external-url", "http://$host:$reservedPort")
        for (config in notificationConfigs) {
            commandFlags.addAll(
                listOf(
                    "-event.config",
                    "bucket=${config.bucket()};project=${config.pubsubProjectId()};" +
                            "topic=${config.pubsubTopic()}",
                )
            )
        }
        pubSubConnectionItem.ifPresent { pubsub ->
            withNetwork(pubsub.network)
            withEnv("PUBSUB_EMULATOR_HOST", "${pubsub.internalHost}:${pubsub.internalPort}")
        }

        withCommand(*commandFlags.toTypedArray())
    }
}
