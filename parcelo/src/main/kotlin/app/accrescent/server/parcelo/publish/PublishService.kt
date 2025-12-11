// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.publish

import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.util.TempFile
import app.accrescent.server.parcelo.util.sha256Hash
import io.grpc.Status
import io.minio.MinioClient
import io.minio.UploadObjectArgs
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.copyTo
import kotlin.io.path.Path
import kotlin.io.path.outputStream
import kotlin.use

@ApplicationScoped
class PublishService @Inject constructor(
    private val config: ParceloConfig,
    private val minioClient: MinioClient,
) {
    fun publishApks(
        appId: String,
        versionCode: Int,
        apkSetPath: Path,
        apkPaths: Set<String>,
    ): Map<String, PublishedApk> {
        val pathsToPublishedApks = mutableMapOf<String, PublishedApk>()

        ZipFile(apkSetPath.toFile()).use { zipFile ->
            for (apkPath in apkPaths) {
                val entry = zipFile.getEntry(apkPath) ?: throw Status
                    .DATA_LOSS
                    .withDescription("app package missing APK entry")
                    .asRuntimeException()
                zipFile.getInputStream(entry).use { apkInputStream ->
                    TempFile(Path(config.packageProcessingDirectory())).use { tempApk ->
                        val bucketId = config.publishedArtifactBucket()
                        val objectId = apkPathToObjectId(apkPath, appId, versionCode)
                        val size = tempApk.path.outputStream().use { apkInputStream.copyTo(it) }
                        pathsToPublishedApks[apkPath] = PublishedApk(
                            bucketId = bucketId,
                            objectId = objectId,
                            size = size.toULong(),
                        )

                        val requestArgs = UploadObjectArgs
                            .builder()
                            .bucket(config.publishedArtifactBucket())
                            .`object`(objectId)
                            .filename(tempApk.path.toString())
                            .build()
                        minioClient.uploadObject(requestArgs)
                    }
                }
            }
        }

        return pathsToPublishedApks
    }

    private fun apkPathToObjectId(apkPath: String, appId: String, versionCode: Int): String {
        val pathHash = sha256Hash(apkPath.toByteArray())
        val objectId = "apps/$appId/$versionCode/$pathHash.apk"

        return objectId
    }
}

data class PublishedApk(val bucketId: String, val objectId: String, val size: ULong)
