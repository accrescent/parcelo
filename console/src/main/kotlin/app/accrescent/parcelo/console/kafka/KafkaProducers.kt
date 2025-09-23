// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.kafka

import app.accrescent.parcelo.console.serde.AppEditPublicationRequestedSerializer
import app.accrescent.parcelo.console.serde.AppPublicationRequestedSerializer
import build.buf.gen.accrescent.server.events.v1.AppEditPublicationRequested
import build.buf.gen.accrescent.server.events.v1.AppPublicationRequested
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties
import java.util.UUID

fun configureKafkaProducers(
    bootstrapServers: String,
    securityProtocol: String?,
    saslMechanism: String?,
    saslLoginCallbackHandlerClass: String?,
    saslJaasConfig: String?,
): Pair<KafkaProducer<String, AppPublicationRequested>, KafkaProducer<String, AppEditPublicationRequested>> {
    val transactionalIdSuffix = UUID.randomUUID().toString()

    val appPublicationRequestedSettings = Properties()
    appPublicationRequestedSettings.put("bootstrap.servers", bootstrapServers)
    // Recommended default from KIP-447 (the default for Kakfa Streams)
    appPublicationRequestedSettings.put("transaction.timeout.ms", 10000)
    // Using a unique transactional ID on startup is safe as of KIP-447 (Kafka 2.6.0, which is no
    // longer supported)
    appPublicationRequestedSettings.put(
        "transactional.id",
        "app.accrescent.server.parcelo-app-publication-requested-$transactionalIdSuffix",
    )
    if (securityProtocol != null) {
        appPublicationRequestedSettings["security.protocol"] = securityProtocol
    }
    if (saslMechanism != null) {
        appPublicationRequestedSettings["sasl.mechanism"] = saslMechanism
    }
    if (saslLoginCallbackHandlerClass != null) {
        appPublicationRequestedSettings["sasl.login.callback.handler.class"] = saslLoginCallbackHandlerClass
    }
    if (saslJaasConfig != null) {
        appPublicationRequestedSettings["sasl.jaas.config"] = saslJaasConfig
    }

    val appPublicationRequestedProducer = KafkaProducer(
        appPublicationRequestedSettings,
        StringSerializer(),
        AppPublicationRequestedSerializer(),
    )
    appPublicationRequestedProducer.initTransactions()

    val appEditPublicationRequestedSettings = Properties()
    appEditPublicationRequestedSettings.put("bootstrap.servers", bootstrapServers)
    // Recommended default from KIP-447 (the default for Kakfa Streams)
    appEditPublicationRequestedSettings.put("transaction.timeout.ms", 10000)
    // Using a unique transactional ID on startup is safe as of KIP-447 (Kafka 2.6.0, which is no
    // longer supported)
    appEditPublicationRequestedSettings.put(
        "transactional.id",
        "app.accrescent.server.parcelo-app-edit-publication-requested-$transactionalIdSuffix",
    )
    if (securityProtocol != null) {
        appEditPublicationRequestedSettings["security.protocol"] = securityProtocol
    }
    if (saslMechanism != null) {
        appEditPublicationRequestedSettings["sasl.mechanism"] = saslMechanism
    }
    if (saslLoginCallbackHandlerClass != null) {
        appEditPublicationRequestedSettings["sasl.login.callback.handler.class"] = saslLoginCallbackHandlerClass
    }
    if (saslJaasConfig != null) {
        appEditPublicationRequestedSettings["sasl.jaas.config"] = saslJaasConfig
    }

    val appEditPublicationRequestedProducer = KafkaProducer(
        appEditPublicationRequestedSettings,
        StringSerializer(),
        AppEditPublicationRequestedSerializer(),
    )
    appEditPublicationRequestedProducer.initTransactions()

    return Pair(appPublicationRequestedProducer, appEditPublicationRequestedProducer)
}
