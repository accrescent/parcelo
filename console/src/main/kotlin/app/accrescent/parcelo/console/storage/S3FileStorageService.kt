// Copyright 2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.storage

import app.accrescent.parcelo.console.data.File
import app.accrescent.parcelo.console.data.Files
import app.accrescent.parcelo.console.data.Files.deleted
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider.Companion.invoke
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.Delete
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.DeleteObjectsRequest
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.ObjectIdentifier
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.asByteStream
import aws.smithy.kotlin.runtime.content.toInputStream
import aws.smithy.kotlin.runtime.net.url.Url
import io.ktor.utils.io.core.use
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.not
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Implementation of [FileStorageService] using S3-compatible storage as the backing store
 */
class S3FileStorageService(
    private val s3EndpointUrl: Url,
    private val s3Region: String,
    private val s3Bucket: String,
    private val s3AccessKeyId: String,
    private val s3SecretAccessKey: String,
) : FileStorageService {
    override suspend fun saveFile(inputStream: InputStream, size: Long): EntityID<Int> {
        S3Client {
            endpointUrl = s3EndpointUrl
            region = s3Region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = s3AccessKeyId
                secretAccessKey = s3SecretAccessKey
            }
        }.use { s3Client ->
            val objectKey = UUID.randomUUID().toString()

            val req = PutObjectRequest {
                bucket = s3Bucket
                key = objectKey
                body = inputStream.asByteStream(size)
            }
            s3Client.putObject(req)

            val fileId = transaction { File.new { s3ObjectKey = objectKey }.id }

            return fileId
        }
    }

    override suspend fun markDeleted(id: Int) {
        transaction { findFile(id)?.apply { deleted = true } } ?: return
    }

    override suspend fun cleanFile(id: Int) {
        val file = transaction { File.findById(id) } ?: return
        val s3ObjectKey = file.s3ObjectKey

        S3Client {
            endpointUrl = s3EndpointUrl
            region = s3Region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = s3AccessKeyId
                secretAccessKey = s3SecretAccessKey
            }
        }.use { s3Client ->
            val req = DeleteObjectRequest {
                bucket = s3Bucket
                key = s3ObjectKey
            }
            s3Client.deleteObject(req)
        }

        transaction { file.delete() }
    }

    override suspend fun cleanAllFiles() {
        val files = transaction { File.find { deleted eq true } }

        val deleteObjectsRequest = transaction {
            files
                .map { ObjectIdentifier { key = it.s3ObjectKey } }
                .let { Delete { objects = it } }
                .let {
                    DeleteObjectsRequest {
                        bucket = s3Bucket
                        delete = it
                    }
                }
        }

        S3Client {
            endpointUrl = s3EndpointUrl
            region = s3Region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = s3AccessKeyId
                secretAccessKey = s3SecretAccessKey
            }
        }.use { s3Client ->
            s3Client.deleteObjects(deleteObjectsRequest)

            transaction { files.forEach { it.delete() } }
        }
    }

    override suspend fun <T> loadFile(id: EntityID<Int>, block: suspend (InputStream) -> T): T {
        val s3ObjectKey =
            transaction { findFile(id.value)?.s3ObjectKey } ?: throw FileNotFoundException()

        S3Client {
            endpointUrl = s3EndpointUrl
            region = s3Region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = s3AccessKeyId
                secretAccessKey = s3SecretAccessKey
            }
        }.use { s3Client ->
            val req = GetObjectRequest {
                bucket = s3Bucket
                key = s3ObjectKey
            }
            val result = s3Client.getObject(req) { response ->
                response.body?.toInputStream()?.use { block(it) } ?: throw IOException()
            }

            return result
        }
    }

    /**
     * Finds the given non-deleted file
     *
     * If the file is marked deleted, returns null.
     *
     * Note: This function requires being wrapped in a transaction context.
     */
    private fun findFile(id: Int): File? {
        return File.find { Files.id eq id and not(deleted) }.singleOrNull()
    }
}
