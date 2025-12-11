// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.minio

import io.minio.MinioClient
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Singleton

@ApplicationScoped
class MinioClientProducer @Inject constructor(private val minioConfig: MinioConfig) {
    @Produces
    @Singleton
    fun createMinioClient(): MinioClient {
        val client = MinioClient
            .builder()
            .endpoint(minioConfig.host())
            .credentials(minioConfig.accessKey(), minioConfig.secretKey())
            .build()

        return client
    }
}
