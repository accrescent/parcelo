// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.serde

import build.buf.gen.accrescent.server.events.v1.AppPublished
import build.buf.protovalidate.ValidatorFactory
import org.apache.kafka.common.serialization.Deserializer

/**
 * Kafka deserializer for [AppPublished] events
 *
 * In addition to basic deserialization, this deserializer also performs validation on all fields of
 * the event. Thus, event consumers can safely assume the event has already been validated when they
 * receive it.
 */
class AppPublishedDeserializer : Deserializer<AppPublished> {
    private val validator = ValidatorFactory
        .newBuilder()
        .buildWithDescriptors(listOf(AppPublished.getDescriptor()), true)

    override fun deserialize(topic: String, data: ByteArray): AppPublished {
        val message = AppPublished.parseFrom(data)

        val validationResult = validator.validate(message)
        if (!validationResult.isSuccess) {
            throw IllegalArgumentException(validationResult.toString())
        }

        return message
    }
}
