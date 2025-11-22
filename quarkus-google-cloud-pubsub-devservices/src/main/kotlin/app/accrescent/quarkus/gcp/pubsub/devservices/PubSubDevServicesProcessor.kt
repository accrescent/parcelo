// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.gcp.pubsub.devservices

import app.accrescent.quarkus.gcp.pubsub.spi.PubSubConnectionItem
import com.google.api.gax.core.NoCredentialsProvider
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.FixedTransportChannelProvider
import com.google.cloud.pubsub.v1.SubscriptionAdminClient
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings
import com.google.cloud.pubsub.v1.TopicAdminClient
import com.google.cloud.pubsub.v1.TopicAdminSettings
import com.google.pubsub.v1.PushConfig
import com.google.pubsub.v1.SubscriptionName
import com.google.pubsub.v1.TopicName
import io.grpc.ManagedChannelBuilder
import io.quarkus.deployment.annotations.BuildProducer
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.DevServicesResultBuildItem
import io.quarkus.devservices.common.ConfigureUtil
import org.testcontainers.containers.Network
import org.testcontainers.containers.PubSubEmulatorContainer
import org.testcontainers.utility.DockerImageName
import java.util.Optional
import java.util.OptionalInt
import kotlin.jvm.optionals.getOrDefault

private const val ACK_DEADLINE_SECONDS = 30
private const val CONFIG_PREFIX = "quarkus.google.cloud.pubsub"
private const val DEFAULT_PROJECT_ID = "dev-project"
private const val DEV_SERVICE_NAME = "google-cloud-pubsub-emulator"
private const val DOCKER_IMAGE_NAME = "gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators"
private const val FEATURE = "google-cloud-pubsub-devservices"
private const val NETWORK_ALIAS = "pubsub-emulator"
private const val PUB_SUB_EMULATOR_PORT = 8085

class PubSubDevServicesProcessor {
    @BuildStep
    fun startContainer(
        config: PubSubDevServicesConfig,
        pubSubConnectionProducer: BuildProducer<PubSubConnectionItem>,
    ): DevServicesResultBuildItem? {
        if (!config.enabled()) {
            return null
        }

        val network = Network.newNetwork()
        val container = QuarkusPubSubContainer(
            config.imageName(),
            config.port(),
            network,
        )
        container.start()

        val host = "${container.host}:${container.firstMappedPort}"

        val topics = config.topics()
        if (topics.isNotEmpty()) {
            val managedChannel = ManagedChannelBuilder.forTarget(host).usePlaintext().build()
            val channelProvider = FixedTransportChannelProvider
                .create(GrpcTransportChannel.create(managedChannel))
            val topicClient = TopicAdminClient
                .create(
                    TopicAdminSettings
                        .newBuilder()
                        .setTransportChannelProvider(channelProvider)
                        .setCredentialsProvider(NoCredentialsProvider())
                        .build()
                )
            val subscriptionClient = SubscriptionAdminClient
                .create(
                    SubscriptionAdminSettings
                        .newBuilder()
                        .setTransportChannelProvider(channelProvider)
                        .setCredentialsProvider(NoCredentialsProvider())
                        .build()
                )

            for ((topicName, topicConfig) in topics) {
                val projectId = config.projectId().getOrDefault(DEFAULT_PROJECT_ID)

                topicClient.createTopic(TopicName.of(projectId, topicName))

                topicConfig.subscriptionNames().ifPresent { subscriptionNames ->
                    for (subscriptionName in subscriptionNames) {
                        subscriptionClient.createSubscription(
                            SubscriptionName.of(projectId, subscriptionName),
                            TopicName.of(projectId, topicName),
                            PushConfig.getDefaultInstance(),
                            ACK_DEADLINE_SECONDS,
                        )
                    }
                }
            }
        }

        // Notify other extensions (namely Google Cloud Storage) that the Pub/Sub Dev Service has
        // started
        pubSubConnectionProducer
            .produce(
                PubSubConnectionItem(
                    network,
                    container.networkAliases.first(),
                    PUB_SUB_EMULATOR_PORT,
                )
            )

        val devService = DevServicesResultBuildItem
            .discovered()
            .name(FEATURE)
            .description("Google Cloud Pub/Sub Dev Service")
            .config(mapOf("$CONFIG_PREFIX.host" to "${container.host}:${container.firstMappedPort}"))
            .build()

        return devService
    }
}

private class QuarkusPubSubContainer(
    imageName: Optional<String>,
    private val fixedExposedPort: OptionalInt,
    private val network: Network,
) : PubSubEmulatorContainer(
    DockerImageName
        .parse(imageName.orElseGet { ConfigureUtil.getDefaultImageNameFor(DEV_SERVICE_NAME) })
        .asCompatibleSubstituteFor(DOCKER_IMAGE_NAME)
) {
    override fun configure() {
        super.configure()

        if (fixedExposedPort.isPresent) {
            addFixedExposedPort(fixedExposedPort.asInt, PUB_SUB_EMULATOR_PORT)
        } else {
            addExposedPort(PUB_SUB_EMULATOR_PORT)
        }

        withNetwork(network)
        withNetworkAliases(NETWORK_ALIAS)
    }
}
