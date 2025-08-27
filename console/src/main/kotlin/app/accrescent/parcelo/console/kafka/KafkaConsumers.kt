// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.kafka

import app.accrescent.parcelo.console.data.AccessControlList
import app.accrescent.parcelo.console.data.App
import app.accrescent.parcelo.console.data.Draft
import app.accrescent.parcelo.console.data.Drafts
import app.accrescent.parcelo.console.data.Edit
import app.accrescent.parcelo.console.data.Listing
import app.accrescent.parcelo.console.data.Listings
import app.accrescent.parcelo.console.data.Update
import app.accrescent.parcelo.console.serde.AppEditPublishedDeserializer
import app.accrescent.parcelo.console.serde.AppPublishedDeserializer
import build.buf.gen.accrescent.server.events.v1.AppEditPublished
import build.buf.gen.accrescent.server.events.v1.AppPublished
import build.buf.gen.accrescent.server.events.v1.ReleaseChannel
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.util.Properties
import java.util.UUID

fun configureKafkaConsumers(
    bootstrapServers: String,
    appPublishedTopic: String,
    appEditPublishedTopic: String,
): Pair<KafkaConsumer<String, AppPublished>, KafkaConsumer<String, AppEditPublished>> {
    val appPublishedSettings = Properties()
    appPublishedSettings.put("bootstrap.servers", bootstrapServers)
    appPublishedSettings.put("enable.auto.commit", "false")
    appPublishedSettings.put("group.id", "app.accrescent.server.parcelo")
    appPublishedSettings.put("isolation.level", "read_committed")

    val appPublishedConsumer = KafkaConsumer(
        appPublishedSettings,
        StringDeserializer(),
        AppPublishedDeserializer(),
    )
    appPublishedConsumer.subscribe(listOf(appPublishedTopic))

    val appEditPublishedSettings = Properties()
    appEditPublishedSettings.put("bootstrap.servers", bootstrapServers)
    appEditPublishedSettings.put("enable.auto.commit", "false")
    appEditPublishedSettings.put("group.id", "app.accrescent.server.parcelo")
    appEditPublishedSettings.put("isolation.level", "read_committed")

    val appEditPublishedConsumer = KafkaConsumer(
        appEditPublishedSettings,
        StringDeserializer(),
        AppEditPublishedDeserializer(),
    )
    appEditPublishedConsumer.subscribe(listOf(appEditPublishedTopic))

    return Pair(appPublishedConsumer, appEditPublishedConsumer)
}

fun startAppPublishedConsumerLoop(consumer: KafkaConsumer<String, AppPublished>) {
    while (true) {
        val records = consumer.poll(Duration.ofMillis(100))
        for (record in records) {
            val event = record.value()

            val stableAppMetadata = event
                .app
                .packageMetadataList
                // protovalidate ensures this element always exists
                .find { it.releaseChannel.wellKnown == ReleaseChannel.WellKnown.WELL_KNOWN_STABLE }!!
                .packageMetadata

            transaction {
                val draft = Draft
                    .find { Drafts.appId eq event.app.appId and (Drafts.publishing eq true) }
                    .single()
                draft.delete()

                val app = App.new(event.app.appId) {
                    versionCode = draft.versionCode
                    versionName = draft.versionName
                    fileId = draft.fileId
                    reviewIssueGroupId = draft.reviewIssueGroupId
                    buildApksResult = ExposedBlob(stableAppMetadata.buildApksResult.toByteArray())
                }
                Listing.new {
                    appId = app.id
                    locale = "en-US"
                    iconId = draft.iconId
                    label = draft.label
                    shortDescription = draft.shortDescription
                }
                AccessControlList.new {
                    userId = draft.creatorId
                    appId = app.id
                    update = true
                    editMetadata = true
                }
            }
        }
        consumer.commitSync()
    }
}

fun startAppEditPublishedConsumerLoop(consumer: KafkaConsumer<String, AppEditPublished>) {
    while (true) {
        val records = consumer.poll(Duration.ofMillis(100))
        for (record in records) {
            val event = record.value()

            val stableAppMetadata = event
                .edit
                .app
                .packageMetadataList
                // protovalidate ensures this element always exists
                .find { it.releaseChannel.wellKnown == ReleaseChannel.WellKnown.WELL_KNOWN_STABLE }!!
                .packageMetadata
            val defaultListing = event
                .edit
                .app
                .listingsList
                // protovalidate ensures this element always exists
                .find { it.language == event.edit.app.defaultListingLanguage }!!

            val updateId = if (event.edit.id.startsWith("update-")) {
                UUID.fromString(event.edit.id.removePrefix("update-"))
            } else {
                null
            }

            transaction {
                val update = updateId?.let { Update.findById(it) }
                App.findById(event.edit.app.appId)?.run {
                    versionCode = stableAppMetadata.versionCode
                    versionName = stableAppMetadata.versionName
                    buildApksResult = ExposedBlob(stableAppMetadata.buildApksResult.toByteArray())
                    if (update != null) {
                        fileId = update.fileId!!
                        update.published = true
                    }
                    updating = false
                }

                if (updateId == null) {
                    val edit = Edit.findById(UUID.fromString(event.edit.id))!!

                    Listing
                        .find { Listings.appId eq event.edit.app.appId and (Listings.locale eq "en-US") }
                        .singleOrNull()
                        ?.run {
                            shortDescription = defaultListing.shortDescription
                        }
                    edit.published = true
                }
            }
        }
        consumer.commitSync()
    }
}
