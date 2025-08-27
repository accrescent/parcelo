// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.serde

import build.buf.gen.accrescent.server.events.v1.AppEditPublicationRequested
import build.buf.protovalidate.ValidatorFactory
import org.apache.kafka.common.serialization.Serializer

/**
 * Kafka serializer for [AppEditPublicationRequested] events
 */
class AppEditPublicationRequestedSerializer : Serializer<AppEditPublicationRequested> {
    private val validator = ValidatorFactory
        .newBuilder()
        .buildWithDescriptors(listOf(AppEditPublicationRequested.getDescriptor()), true)

    override fun serialize(topic: String, data: AppEditPublicationRequested): ByteArray {
        // We don't have robust tests to ensure that we always produce valid messages, so validate
        // our message before serializing it to provide this assurance
        val validationResult = validator.validate(data)
        if (validationResult.isSuccess) {
            return data.toByteArray()
        } else {
            throw IllegalArgumentException(validationResult.toString())
        }
    }
}
