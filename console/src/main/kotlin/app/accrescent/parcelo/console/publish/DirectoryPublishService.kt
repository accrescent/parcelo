// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.publish

import app.accrescent.parcelo.apksparser.ApkSet
import app.accrescent.parcelo.apksparser.ParseApkSetResult
import app.accrescent.parcelo.console.data.Listing
import app.accrescent.parcelo.console.data.Listings
import app.accrescent.parcelo.console.util.TempFile
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.net.url.Url
import build.buf.gen.accrescent.server.events.v1.AppEditPublicationRequested
import build.buf.gen.accrescent.server.events.v1.AppKt.packageMetadataEntry
import build.buf.gen.accrescent.server.events.v1.AppPublicationRequested
import build.buf.gen.accrescent.server.events.v1.ObjectMetadata
import build.buf.gen.accrescent.server.events.v1.ReleaseChannel
import build.buf.gen.accrescent.server.events.v1.app
import build.buf.gen.accrescent.server.events.v1.appEdit
import build.buf.gen.accrescent.server.events.v1.appEditPublicationRequested
import build.buf.gen.accrescent.server.events.v1.appListing
import build.buf.gen.accrescent.server.events.v1.appPublicationRequested
import build.buf.gen.accrescent.server.events.v1.image
import build.buf.gen.accrescent.server.events.v1.objectMetadata
import build.buf.gen.accrescent.server.events.v1.packageMetadata
import build.buf.gen.accrescent.server.events.v1.releaseChannel
import build.buf.gen.android.bundle.BuildApksResult
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

private const val TABLE_OF_CONTENTS_PATH = "toc.pb"

class DirectoryPublishService(
    private val s3EndpointUrl: Url,
    private val s3Region: String,
    private val s3Bucket: String,
    private val s3AccessKeyId: String,
    private val s3SecretAccessKey: String,
    private val appPublicationRequestedProducer: KafkaProducer<String, AppPublicationRequested>,
    private val appPublicationRequestedTopic: String,
    private val appEditPublicationRequestedProducer: KafkaProducer<String, AppEditPublicationRequested>,
    private val appEditPublicationRequestedTopic: String,
) : PublishService {
    override suspend fun publishDraft(
        apkSet: InputStream,
        icon: InputStream,
        appName: String,
        shortDescription: String
    ) {
        TempFile().use { tempApkSet ->
            tempApkSet.outputStream().use { apkSet.copyTo(it) }

            val parseResult = ApkSet.parse(tempApkSet.path.toFile())
            val metadata = when (parseResult) {
                is ParseApkSetResult.Ok -> parseResult.apkSet
                is ParseApkSetResult.Error -> throw Exception("APK set parsing failed")
            }
            val appId = metadata.metadata.packageName
            val versionCode = metadata.versionCode
            val versionName = metadata.versionName
            val buildApksResult = BuildApksResult.parseFrom(metadata.metadata.toByteArray())

            val apkObjectMetadata = mutableMapOf<String, ObjectMetadata>()

            // Publish the APKs and app icon
            val iconObjectId = S3Client {
                endpointUrl = s3EndpointUrl
                region = s3Region
                credentialsProvider = StaticCredentialsProvider {
                    accessKeyId = s3AccessKeyId
                    secretAccessKey = s3SecretAccessKey
                }
            }.use { s3Client ->
                val hasher = MessageDigest.getInstance("SHA-256")

                ZipFile(tempApkSet.path.toFile()).use { zip ->
                    for (entry in zip.entries()) {
                        // We don't need to publish the table of contents, and directories aren't
                        // publishable
                        if (entry.name == TABLE_OF_CONTENTS_PATH || entry.isDirectory) {
                            continue
                        }

                        val apkPathHash = hasher
                            .digest(entry.name.toByteArray())
                            .toHexString()

                        val entryBytes = zip.getInputStream(entry).readBytes()
                        // This key construction is deterministic, so we can safely retry this
                        // operation later in case of failure without creating orphan objects (i.e.
                        // objects whose existence our server isn't aware of)
                        val objectId = "apps/$appId/$versionCode/$apkPathHash"

                        val request = PutObjectRequest {
                            bucket = s3Bucket
                            key = objectId
                            body = ByteStream.fromBytes(entryBytes)
                        }
                        s3Client.putObject(request)

                        apkObjectMetadata.put(
                            entry.name,
                            objectMetadata {
                                id = objectId
                                uncompressedSize = entryBytes.size
                            },
                        )
                    }
                }

                val iconBytes = icon.readBytes()
                val iconHash = hasher.digest(iconBytes).toHexString()
                val iconObjectId = "apps/$appId/$iconHash.png"

                val request = PutObjectRequest {
                    bucket = s3Bucket
                    key = iconObjectId
                    body = ByteStream.fromBytes(iconBytes)
                }
                s3Client.putObject(request)

                iconObjectId
            }

            // Publish the app metadata to the directory service
            try {
                appPublicationRequestedProducer.beginTransaction()

                val message = appPublicationRequested {
                    app = app {
                        this.appId = appId
                        defaultListingLanguage = "en-US"
                        listings.add(appListing {
                            language = "en-US"
                            name = appName
                            this.shortDescription = shortDescription
                            this.icon = image {
                                objectId = iconObjectId
                            }
                        })
                        packageMetadata.add(packageMetadataEntry {
                            releaseChannel = releaseChannel {
                                wellKnown = ReleaseChannel.WellKnown.WELL_KNOWN_STABLE
                            }
                            packageMetadata = packageMetadata {
                                this.versionCode = versionCode.toLong()
                                this.versionName = versionName
                                this.buildApksResult = buildApksResult
                                this.apkObjectMetadata.putAll(apkObjectMetadata)
                            }
                        })
                    }
                }
                appPublicationRequestedProducer.send(
                    ProducerRecord(appPublicationRequestedTopic, message)
                )

                appPublicationRequestedProducer.commitTransaction()
            } catch (_: KafkaException) {
                appPublicationRequestedProducer.abortTransaction()
            }
        }
    }

    override suspend fun publishUpdate(
        apkSet: InputStream,
        updateId: UUID,
        currentIcon: InputStream,
        currentAppName: String,
        currentShortDescription: String,
    ) {
        return TempFile().use { tempApkSet ->
            tempApkSet.outputStream().use { apkSet.copyTo(it) }

            val parseResult = ApkSet.parse(tempApkSet.path.toFile())
            val metadata = when (parseResult) {
                is ParseApkSetResult.Ok -> parseResult.apkSet
                is ParseApkSetResult.Error -> throw Exception("APK set parsing failed")
            }
            val appId = metadata.metadata.packageName
            val versionCode = metadata.versionCode
            val versionName = metadata.versionName
            val buildApksResult = BuildApksResult.parseFrom(metadata.metadata.toByteArray())

            val apkObjectMetadata = mutableMapOf<String, ObjectMetadata>()

            // Publish the APKs
            val iconObjectId = S3Client {
                endpointUrl = s3EndpointUrl
                region = s3Region
                credentialsProvider = StaticCredentialsProvider {
                    accessKeyId = s3AccessKeyId
                    secretAccessKey = s3SecretAccessKey
                }
            }.use { s3Client ->
                val hasher = MessageDigest.getInstance("SHA-256")

                ZipFile(tempApkSet.path.toFile()).use { zip ->
                    for (entry in zip.entries()) {
                        // We don't need to publish the table of contents, and directories aren't
                        // publishable
                        if (entry.name == TABLE_OF_CONTENTS_PATH || entry.isDirectory) {
                            continue
                        }

                        val apkPathHash = hasher
                            .digest(entry.name.toByteArray())
                            .toHexString()

                        val entryBytes = zip.getInputStream(entry).readBytes()
                        // This key construction is deterministic, so we can safely retry this
                        // operation later in case of failure without creating orphan objects (i.e.
                        // objects whose existence our server isn't aware of)
                        val objectId = "apps/$appId/$versionCode/$apkPathHash"

                        val request = PutObjectRequest {
                            bucket = s3Bucket
                            key = objectId
                            body = ByteStream.fromBytes(entryBytes)
                        }
                        s3Client.putObject(request)

                        apkObjectMetadata.put(
                            entry.name,
                            objectMetadata {
                                id = objectId
                                uncompressedSize = entryBytes.size
                            },
                        )
                    }
                }

                val iconBytes = currentIcon.readBytes()
                val iconHash = hasher.digest(iconBytes).toHexString()
                val iconObjectId = "apps/$appId/$iconHash.png"

                val request = PutObjectRequest {
                    bucket = s3Bucket
                    key = iconObjectId
                    body = ByteStream.fromBytes(iconBytes)
                }
                s3Client.putObject(request)

                iconObjectId
            }

            // Publish the app metadata to the directory service
            try {
                appEditPublicationRequestedProducer.beginTransaction()

                val message = appEditPublicationRequested {
                    edit = appEdit {
                        id = "update-$updateId"
                        app = app {
                            this.appId = appId
                            defaultListingLanguage = "en-US"
                            listings.add(appListing {
                                language = "en-US"
                                name = currentAppName
                                shortDescription = currentShortDescription
                                icon = image {
                                    objectId = iconObjectId
                                }
                            })
                            packageMetadata.add(packageMetadataEntry {
                                releaseChannel = releaseChannel {
                                    wellKnown = ReleaseChannel.WellKnown.WELL_KNOWN_STABLE
                                }
                                packageMetadata = packageMetadata {
                                    this.versionCode = versionCode.toLong()
                                    this.versionName = versionName
                                    this.buildApksResult = buildApksResult
                                    this.apkObjectMetadata.putAll(apkObjectMetadata)
                                }
                            })
                        }
                    }
                }
                appEditPublicationRequestedProducer.send(
                    ProducerRecord(appEditPublicationRequestedTopic, message)
                )

                appEditPublicationRequestedProducer.commitTransaction()
            } catch (_: KafkaException) {
                appEditPublicationRequestedProducer.abortTransaction()
            }

            buildApksResult.toByteArray()
        }
    }

    override suspend fun publishEdit(
        appId: String,
        editId: UUID,
        currentApkSet: InputStream,
        currentIcon: InputStream,
        shortDescription: String?,
    ) {
        val appListing = transaction {
            Listing.find { Listings.appId eq appId and (Listings.locale eq "en-US") }.single()
        }

        TempFile().use { tempApkSet ->
            tempApkSet.outputStream().use { currentApkSet.copyTo(it) }

            val parseResult = ApkSet.parse(tempApkSet.path.toFile())
            val metadata = when (parseResult) {
                is ParseApkSetResult.Ok -> parseResult.apkSet
                is ParseApkSetResult.Error -> throw Exception("APK set parsing failed")
            }
            val versionCode = metadata.versionCode
            val versionName = metadata.versionName
            val buildApksResult = BuildApksResult.parseFrom(metadata.metadata.toByteArray())

            val apkObjectMetadata = mutableMapOf<String, ObjectMetadata>()

            val hasher = MessageDigest.getInstance("SHA-256")
            ZipFile(tempApkSet.path.toFile()).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.name == TABLE_OF_CONTENTS_PATH || entry.isDirectory) {
                        continue
                    }

                    val apkPathHash = hasher
                        .digest(entry.name.toByteArray())
                        .toHexString()

                    val entryBytes = zip.getInputStream(entry).readBytes()
                    val objectId = "apps/$appId/$versionCode/$apkPathHash"
                    apkObjectMetadata.put(
                        entry.name,
                        objectMetadata {
                            id = objectId
                            uncompressedSize = entryBytes.size
                        },
                    )
                }
            }

            val iconBytes = currentIcon.readBytes()
            val iconHash = hasher.digest(iconBytes).toHexString()
            val iconObjectId = "apps/$appId/$iconHash.png"

            try {
                appEditPublicationRequestedProducer.beginTransaction()

                val message = appEditPublicationRequested {
                    edit = appEdit {
                        id = editId.toString()
                        this.app = app {
                            this.appId = appId
                            defaultListingLanguage = "en-US"
                            listings.add(appListing {
                                language = "en-US"
                                name = appListing.label
                                this.shortDescription = shortDescription ?: appListing.shortDescription
                                icon = image {
                                    objectId = iconObjectId
                                }
                            })
                            packageMetadata.add(packageMetadataEntry {
                                releaseChannel = releaseChannel {
                                    wellKnown = ReleaseChannel.WellKnown.WELL_KNOWN_STABLE
                                }
                                packageMetadata = packageMetadata {
                                    this.versionCode = versionCode.toLong()
                                    this.versionName = versionName
                                    this.buildApksResult = buildApksResult
                                    this.apkObjectMetadata.putAll(apkObjectMetadata)
                                }
                            })
                        }
                    }
                }
                appEditPublicationRequestedProducer.send(
                    ProducerRecord(appEditPublicationRequestedTopic, message)
                )

                appEditPublicationRequestedProducer.commitTransaction()
            } catch (_: KafkaException) {
                appEditPublicationRequestedProducer.abortTransaction()
            }
        }
    }
}
