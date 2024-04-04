// Copyright 2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.publish

import app.accrescent.parcelo.apksparser.ApkSet
import app.accrescent.parcelo.apksparser.ParseApkSetResult
import app.accrescent.parcelo.console.data.Listing
import app.accrescent.parcelo.console.data.Listings
import app.accrescent.parcelo.console.repo.RepoData
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.Delete
import aws.sdk.kotlin.services.s3.model.DeleteObjectsRequest
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.ListObjectsRequest
import aws.sdk.kotlin.services.s3.model.ObjectIdentifier
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toInputStream
import aws.smithy.kotlin.runtime.net.url.Url
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.core.use
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * A [PublishService] that publishes to a remote S3 object storage bucket
 */
class S3PublishService(
    private val s3EndpointUrl: Url,
    private val s3Region: String,
    private val s3Bucket: String,
    private val s3AccessKeyId: String,
    private val s3SecretAccessKey: String,
) : PublishService {
    override suspend fun publishDraft(
        apkSet: InputStream,
        icon: InputStream,
        shortDescription: String,
    ) {
        val apkSetData = apkSet.readBytes()
        val parseResult = apkSetData.inputStream().use { ApkSet.parse(it) }
        val metadata = when (parseResult) {
            is ParseApkSetResult.Ok -> parseResult.apkSet
            is ParseApkSetResult.Error -> throw Exception("APK set parsing failed")
        }

        apkSetData.inputStream().use { apkSetInputStream ->
            ZipInputStream(apkSetInputStream).use { zip ->
                publish(zip, metadata, PublicationType.NewApp(icon, shortDescription))
            }
        }
    }

    override suspend fun publishUpdate(apkSet: InputStream, appId: String) {
        val apkSetData = apkSet.readBytes()
        val parseResult = apkSetData.inputStream().use { ApkSet.parse(it) }
        val metadata = when (parseResult) {
            is ParseApkSetResult.Ok -> parseResult.apkSet
            is ParseApkSetResult.Error -> throw Exception("APK set parsing failed")
        }

        apkSetData.inputStream().use { apkSetInputStream ->
            ZipInputStream(apkSetInputStream).use { zip ->
                publish(zip, metadata, PublicationType.Update)
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun publishEdit(appId: String, shortDescription: String?) {
        val getOldDataReq = GetObjectRequest {
            bucket = s3Bucket
            key = "apps/$appId/repodata.json"
        }
        S3Client {
            endpointUrl = s3EndpointUrl
            region = s3Region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = s3AccessKeyId
                secretAccessKey = s3SecretAccessKey
            }
        }.use { s3Client ->
            val newRepoData = s3Client.getObject(getOldDataReq) { resp ->
                val oldRepoData =
                    resp.body?.toInputStream()?.use { Json.decodeFromStream<RepoData>(it) }
                        ?: throw FileNotFoundException()
                RepoData(
                    version = oldRepoData.version,
                    versionCode = oldRepoData.versionCode,
                    abiSplits = oldRepoData.abiSplits,
                    densitySplits = oldRepoData.densitySplits,
                    langSplits = oldRepoData.langSplits,
                    shortDescription = shortDescription ?: oldRepoData.shortDescription
                )
            }

            val updateDataReq = PutObjectRequest {
                bucket = s3Bucket
                key = "apps/$appId/repodata.json"
                body = ByteStream.fromString(Json.encodeToString(newRepoData))
            }
            s3Client.putObject(updateDataReq)
        }
    }

    private suspend fun publish(zip: ZipInputStream, metadata: ApkSet, type: PublicationType) {
        val appId = metadata.appId.value

        S3Client {
            endpointUrl = s3EndpointUrl
            region = s3Region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = s3AccessKeyId
                secretAccessKey = s3SecretAccessKey
            }
        }.use { s3Client ->
            // Extract split APKs
            generateSequence { zip.nextEntry }
                .filterNot { it.isDirectory }
                .forEach { entry ->
                    // Don't extract any file that doesn't have an associated split name or explicit
                    // lack thereof
                    val splitName = metadata.entrySplitNames[entry.name] ?: return@forEach

                    val fileName = if (splitName.isEmpty) {
                        "base.apk"
                    } else {
                        "split.${splitName.get().replace('_', '-')}.apk"
                    }

                    val request = PutObjectRequest {
                        bucket = s3Bucket
                        key = "apps/$appId/${metadata.versionCode}/$fileName"
                        body = ByteStream.fromBytes(zip.readBytes())
                    }

                    s3Client.putObject(request)
                }

            // Copy icon
            if (type is PublicationType.NewApp) {
                val request = PutObjectRequest {
                    bucket = s3Bucket
                    key = "apps/$appId/icon.png"
                    body = ByteStream.fromBytes(type.icon.readBytes())
                }
                s3Client.putObject(request)
            }

            // Publish repodata
            val shortDescription = if (type is PublicationType.NewApp) {
                type.shortDescription
            } else {
                transaction {
                    Listing.find { Listings.appId eq appId and (Listings.locale eq "en-US") }
                        .single().shortDescription
                }
            }

            val repoData = RepoData(
                version = metadata.versionName,
                versionCode = metadata.versionCode,
                abiSplits = metadata.abiSplits.map { it.replace("_", "-") }.toSet(),
                langSplits = metadata.langSplits,
                densitySplits = metadata.densitySplits,
                shortDescription = shortDescription,
            )
            val repoDataString = Json.encodeToString(repoData)

            val publishRepoDataRequest = PutObjectRequest {
                bucket = s3Bucket
                key = "apps/$appId/repodata.json"
                body = ByteStream.fromBytes(repoDataString.toByteArray())
            }
            s3Client.putObject(publishRepoDataRequest)

            // Delete old split APKs
            if (type is PublicationType.Update) {
                val objectsToDelete = mutableListOf<ObjectIdentifier>()
                val listRequest = ListObjectsRequest {
                    bucket = s3Bucket
                    prefix = "apps/$appId"
                }
                s3Client.listObjects(listRequest).contents?.forEach { obj ->
                    val dirVersionCode =
                        obj.key?.substringBeforeLast('/')?.substringAfterLast('/')?.toIntOrNull()
                            ?: return@forEach
                    if (dirVersionCode < metadata.versionCode) {
                        objectsToDelete.add(ObjectIdentifier { key = obj.key })
                    }
                }
                if (objectsToDelete.isNotEmpty()) {
                    val deleteObject = Delete { objects = objectsToDelete }
                    val deleteRequest = DeleteObjectsRequest {
                        bucket = s3Bucket
                        delete = deleteObject
                    }
                    s3Client.deleteObjects(deleteRequest)
                }
            }
        }
    }
}

private sealed class PublicationType {
    class NewApp(val icon: InputStream, val shortDescription: String) : PublicationType()
    data object Update : PublicationType()
}
