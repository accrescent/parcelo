// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.async

import app.accrescent.quarkus.gcp.pubsub.PubSubHelper
import com.google.cloud.pubsub.v1.SubscriberInterface
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger

private const val EVENT_TYPE_KEY = "eventType"
private const val EVENT_TYPE_OBJECT_FINALIZE = "OBJECT_FINALIZE"
private const val OBJECT_ID_KEY = "objectId"

@ApplicationScoped
class ApkSetUploadedProcessor @Inject constructor(
    private val pubSubHelper: PubSubHelper,

    @ConfigProperty(name = "parcelo.pubsub.project-id")
    private val pubSubProjectId: String,

    @ConfigProperty(name = "parcelo.pubsub.subscription-name")
    private val pubSubSubscriptionName: String,
) {
    private companion object {
        private val LOG = Logger.getLogger(ApkSetUploadedProcessor::class.java)
    }

    private lateinit var subscriber: SubscriberInterface

    fun onStart(@Observes startupEvent: StartupEvent) {
        subscriber = pubSubHelper.createSubscriber(
            pubSubProjectId,
            pubSubSubscriptionName,
        ) { message, ackReplier ->
            val eventType = message.attributesMap[EVENT_TYPE_KEY] ?: run {
                LOG.error("received message without $EVENT_TYPE_KEY key")
                ackReplier.nack()
                return@createSubscriber
            }
            if (eventType != EVENT_TYPE_OBJECT_FINALIZE) {
                LOG.error("expected event type of $EVENT_TYPE_OBJECT_FINALIZE but got $eventType")
                ackReplier.nack()
                return@createSubscriber
            }
            val objectId = message.attributesMap[OBJECT_ID_KEY] ?: run {
                LOG.error("received message without $OBJECT_ID_KEY key")
                ackReplier.nack()
                return@createSubscriber
            }

            LOG.info("Object uploaded with ID $objectId")

            ackReplier.ack()
        }
        subscriber.startAsync().awaitRunning()
    }

    fun onStop(@Observes shutdownEvent: ShutdownEvent) {
        subscriber.stopAsync()
    }
}
