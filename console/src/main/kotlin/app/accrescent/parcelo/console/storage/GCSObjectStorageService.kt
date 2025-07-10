// Copyright 2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.storage

import app.accrescent.parcelo.console.data.File
import app.accrescent.parcelo.console.data.Files
import app.accrescent.parcelo.console.data.Files.deleted
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.StorageOptions
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.channels.Channels
import java.nio.file.Path

/**
 * Implementation of [ObjectStorageService] using a Google Cloud Storage backend
 */
class GCSObjectStorageService(
    projectId: String,
    private val bucket: String,
) : ObjectStorageService {
    private val storage = StorageOptions.newBuilder().setProjectId(projectId).build().service

    override suspend fun uploadFile(path: Path): EntityID<Int> {
        val objectKey = generateObjectId()

        val blobId = BlobId.of(bucket, objectKey)
        val blobInfo = BlobInfo.newBuilder(blobId).build()
        storage.createFrom(blobInfo, path)

        val fileId = transaction { File.new { s3ObjectKey = objectKey }.id }

        return fileId
    }

    override suspend fun uploadBytes(bytes: ByteArray): EntityID<Int> {
        val objectKey = generateObjectId()

        val blobId = BlobId.of(bucket, objectKey)
        val blobInfo = BlobInfo.newBuilder(blobId).build()
        storage.create(blobInfo, bytes)

        val fileId = transaction { File.new { s3ObjectKey = objectKey }.id }

        return fileId
    }

    override suspend fun markDeleted(id: Int) {
        transaction { findFile(id)?.apply { deleted = true } } ?: return
    }

    override suspend fun cleanObject(id: Int) {
        val file = transaction {
            File.find { Files.id eq id and (deleted eq true) }.singleOrNull()
        } ?: return
        val s3ObjectKey = file.s3ObjectKey

        val blobId = BlobId.of(bucket, s3ObjectKey)
        storage.delete(blobId)

        transaction { file.delete() }
    }

    override suspend fun cleanAllObjects() {
        val files = transaction { File.find { deleted eq true } }

        val blobsToDelete = transaction { files.map { BlobId.of(bucket, it.s3ObjectKey) } }

        if (blobsToDelete.isNotEmpty()) {
            storage.delete(blobsToDelete)

            transaction { files.forEach { it.delete() } }
        }
    }

    override suspend fun <T> loadObject(id: EntityID<Int>, block: suspend (InputStream) -> T): T {
        val s3ObjectKey =
            transaction { findFile(id.value)?.s3ObjectKey } ?: throw FileNotFoundException()

        val blobId = BlobId.of(bucket, s3ObjectKey)
        val result = storage.reader(blobId).use { readChannel ->
            Channels.newInputStream(readChannel).use { block(it) }
        }

        return result
    }
}
