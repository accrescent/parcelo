// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.gcp.pubsub

import com.google.api.gax.core.NoCredentialsProvider
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.FixedTransportChannelProvider
import com.google.cloud.pubsub.v1.MessageReceiver
import com.google.cloud.pubsub.v1.Subscriber
import com.google.cloud.pubsub.v1.SubscriberInterface
import com.google.pubsub.v1.ProjectSubscriptionName
import io.grpc.ManagedChannelBuilder
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class PubSubHelper @Inject constructor(private val pubSubConfig: PubSubConfig) {
    fun createSubscriber(
        projectId: String,
        subscription: String,
        receiver: MessageReceiver,
    ): SubscriberInterface {
        val subscriberBuilder = Subscriber
            .newBuilder(ProjectSubscriptionName.of(projectId, subscription), receiver)
        pubSubConfig.host().ifPresent { host ->
            val managedChannel = ManagedChannelBuilder.forTarget(host).usePlaintext().build()
            val grpcChannel = GrpcTransportChannel.create(managedChannel)
            subscriberBuilder.setChannelProvider(FixedTransportChannelProvider.create(grpcChannel))
            subscriberBuilder.setCredentialsProvider(NoCredentialsProvider())
        }
        val subscriber = subscriberBuilder.build()

        return subscriber
    }
}
