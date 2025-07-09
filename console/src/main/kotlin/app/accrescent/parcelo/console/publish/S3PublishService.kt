// Copyright 2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.publish

import app.accrescent.parcelo.apksparser.ApkSet
import app.accrescent.parcelo.apksparser.ParseApkSetResult
import app.accrescent.parcelo.console.data.App
import app.accrescent.parcelo.console.data.Listing
import app.accrescent.parcelo.console.data.Listings
import app.accrescent.parcelo.console.repo.RepoData
import app.accrescent.parcelo.console.util.TempFile
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.Delete
import aws.sdk.kotlin.services.s3.model.DeleteObjectsRequest
import aws.sdk.kotlin.services.s3.model.ListObjectsRequest
import aws.sdk.kotlin.services.s3.model.ObjectIdentifier
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.net.url.Url
import com.android.bundle.Targeting
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.core.use
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.InputStream
import java.util.zip.ZipFile

private const val BASE_MODULE_NAME = "base"

/**
 * A namespace for various screen densities
 *
 * See the corresponding
 * [Android developer documentation](https://developer.android.com/reference/android/util/DisplayMetrics)
 * for reference.
 */
private class DisplayMetrics {
    companion object {
        const val DENSITY_LOW = 120
        const val DENSITY_MEDIUM = 160
        const val DENSITY_TV = 213
        const val DENSITY_HIGH = 240
        const val DENSITY_XHIGH = 320
        const val DENSITY_XXHIGH = 480
    }
}

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
    ): ByteArray {
        TempFile().use { tempApkSet ->
            tempApkSet.outputStream().use { apkSet.copyTo(it) }

            val parseResult = ApkSet.parse(tempApkSet.path.toFile())
            val metadata = when (parseResult) {
                is ParseApkSetResult.Ok -> parseResult.apkSet
                is ParseApkSetResult.Error -> throw Exception("APK set parsing failed")
            }

            return publish(
                ZipFile(tempApkSet.path.toFile()),
                metadata,
                PublicationType.NewApp(icon, shortDescription),
            )
        }
    }

    override suspend fun publishUpdate(apkSet: InputStream, appId: String): ByteArray {
        TempFile().use { tempApkSet ->
            tempApkSet.outputStream().use { apkSet.copyTo(it) }

            val parseResult = ApkSet.parse(tempApkSet.path.toFile())
            val metadata = when (parseResult) {
                is ParseApkSetResult.Ok -> parseResult.apkSet
                is ParseApkSetResult.Error -> throw Exception("APK set parsing failed")
            }

            return publish(ZipFile(tempApkSet.path.toFile()), metadata, PublicationType.Update)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun publishEdit(appId: String, shortDescription: String?): ByteArray {
        S3Client {
            endpointUrl = s3EndpointUrl
            region = s3Region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = s3AccessKeyId
                secretAccessKey = s3SecretAccessKey
            }
        }.use { s3Client ->
            // Fetch the old app metadata from the database
            val app = transaction { App.findById(appId) } ?: throw Exception("app not found")
            val oldRepoData = app.repositoryMetadata.inputStream
                .use { Json.decodeFromStream<RepoData>(it) }

            // Modify the old app metadata to produce the new app metadata
            val newRepoData = RepoData(
                version = oldRepoData.version,
                versionCode = oldRepoData.versionCode,
                abiSplits = oldRepoData.abiSplits,
                densitySplits = oldRepoData.densitySplits,
                langSplits = oldRepoData.langSplits,
                shortDescription = shortDescription ?: oldRepoData.shortDescription
            ).let { Json.encodeToString(it) }.toByteArray()

            // Publish the new app metadata
            val updateDataReq = PutObjectRequest {
                bucket = s3Bucket
                key = "apps/$appId/repodata.json"
                body = ByteStream.fromBytes(newRepoData)
            }
            s3Client.putObject(updateDataReq)

            return newRepoData
        }
    }

    // Note that APKs which target multiple ABIs, multiple languages, or multiple screen densities
    // are not currently supported
    private suspend fun publish(
        apkSetZip: ZipFile,
        metadata: ApkSet,
        type: PublicationType,
    ): ByteArray {
        val appId = metadata.metadata.packageName

        S3Client {
            endpointUrl = s3EndpointUrl
            region = s3Region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = s3AccessKeyId
                secretAccessKey = s3SecretAccessKey
            }
        }.use { s3Client ->
            // Publish split APKs

            val abiSplits = mutableSetOf<String>()
            val langSplits = mutableSetOf<String>()
            val densitySplits = mutableSetOf<String>()

            // Assume for now that the variant with the largest variant number is compatible with
            // all devices. This behavior will change with the migration to the new repository
            // format.
            metadata.metadata.variantList
                // The variant list will never be empty since we successfully parsed the ApkSet
                .maxByOrNull { it.variantNumber }!!
                .apkSetList
                .find { it.moduleMetadata.name == BASE_MODULE_NAME }
                ?.apkDescriptionList
                ?.forEach { apkDescription ->
                    val outputFileName = if (!apkDescription.hasSplitApkMetadata()) {
                        return@forEach
                    } else if (apkDescription.splitApkMetadata.isMasterSplit) {
                        "base.apk"
                    } else if (apkDescription.targeting.hasAbiTargeting()) {
                        if (apkDescription.targeting.abiTargeting.valueCount != 1) {
                            throw Exception("unsupported ABI targeting")
                        }

                        val abi = apkDescription.targeting.abiTargeting.valueList[0]!!.alias
                        val config = when (abi) {
                            Targeting.Abi.AbiAlias.ARMEABI_V7A -> "armeabi-v7a"
                            Targeting.Abi.AbiAlias.ARM64_V8A -> "arm64-v8a"
                            Targeting.Abi.AbiAlias.X86 -> "x86"
                            Targeting.Abi.AbiAlias.X86_64 -> "x86_64"
                            // Simply don't publish ABI splits for architectures we don't support
                            else -> null
                        }
                        config?.let { abiSplits.add(it) }

                        "split.$config.apk"
                    } else if (apkDescription.targeting.hasLanguageTargeting()) {
                        if (apkDescription.targeting.languageTargeting.valueCount != 1) {
                            throw Exception("unsupported language targeting")
                        }

                        val lang = apkDescription.targeting.languageTargeting.valueList[0]!!
                        langSplits.add(lang)

                        "split.$lang.apk"
                    } else if (apkDescription.targeting.hasScreenDensityTargeting()) {
                        if (apkDescription.targeting.screenDensityTargeting.valueCount != 1) {
                            throw Exception("unsupported screen density targeting")
                        }

                        val screenDensity =
                            apkDescription.targeting.screenDensityTargeting.valueList[0]!!
                        val config = when (screenDensity.densityOneofCase) {
                            Targeting.ScreenDensity.DensityOneofCase.DENSITY_ALIAS -> when (screenDensity.densityAlias) {
                                Targeting.ScreenDensity.DensityAlias.NODPI -> "nodpi"
                                Targeting.ScreenDensity.DensityAlias.LDPI -> "ldpi"
                                Targeting.ScreenDensity.DensityAlias.MDPI -> "mdpi"
                                Targeting.ScreenDensity.DensityAlias.TVDPI -> "tvdpi"
                                Targeting.ScreenDensity.DensityAlias.HDPI -> "hdpi"
                                Targeting.ScreenDensity.DensityAlias.XHDPI -> "xhdpi"
                                Targeting.ScreenDensity.DensityAlias.XXHDPI -> "xxhdpi"
                                Targeting.ScreenDensity.DensityAlias.XXXHDPI -> "xxxhdpi"
                                else -> throw Exception("unsupported screen density targeting")
                            }

                            Targeting.ScreenDensity.DensityOneofCase.DENSITY_DPI -> when {
                                screenDensity.densityDpi <= DisplayMetrics.DENSITY_LOW -> "ldpi"
                                screenDensity.densityDpi <= DisplayMetrics.DENSITY_MEDIUM -> "mdpi"
                                screenDensity.densityDpi <= DisplayMetrics.DENSITY_TV -> "tvdpi"
                                screenDensity.densityDpi <= DisplayMetrics.DENSITY_HIGH -> "hdpi"
                                screenDensity.densityDpi <= DisplayMetrics.DENSITY_XHIGH -> "xhdpi"
                                screenDensity.densityDpi <= DisplayMetrics.DENSITY_XXHIGH -> "xxhdpi"
                                else -> "xxxhdpi"
                            }

                            Targeting.ScreenDensity.DensityOneofCase.DENSITYONEOF_NOT_SET ->
                                throw Exception("unsupported screen density targeting")
                        }
                        densitySplits.add(config)

                        "split.$config.apk"
                    } else {
                        throw Exception("unsupported APK targeting")
                    }
                    val entry = apkSetZip.getEntry(apkDescription.path)

                    val request = PutObjectRequest {
                        bucket = s3Bucket
                        key = "apps/$appId/${metadata.versionCode}/$outputFileName"
                        body = ByteStream.fromBytes(apkSetZip.getInputStream(entry).readBytes())
                    }
                    s3Client.putObject(request)
                }
                ?: throw Exception("no base module found")

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
                abiSplits = abiSplits,
                langSplits = langSplits,
                densitySplits = densitySplits,
                shortDescription = shortDescription,
            )
            val repoDataBytes = Json.encodeToString(repoData).toByteArray()

            val publishRepoDataRequest = PutObjectRequest {
                bucket = s3Bucket
                key = "apps/$appId/repodata.json"
                body = ByteStream.fromBytes(repoDataBytes)
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

            return repoDataBytes
        }
    }
}

private sealed class PublicationType {
    class NewApp(val icon: InputStream, val shortDescription: String) : PublicationType()
    data object Update : PublicationType()
}
