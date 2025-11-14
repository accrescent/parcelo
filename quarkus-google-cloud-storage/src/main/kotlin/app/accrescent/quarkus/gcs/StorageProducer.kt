// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.gcs

import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Disposes
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URL
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull

@ApplicationScoped
class StorageProducer @Inject constructor(
    private val storageConfig: StorageConfig,
) {
    @Produces
    @Singleton
    fun createStorage(): Storage {
        val host = storageConfig.host().getOrNull()
        val storage = if (host != null) {
            val storage = StorageOptions
                .newBuilder()
                .setHost(host)
                .setCredentials(generateDevCredentials())
                .build()
                .service
            // Intercept the default signUrl() implementation, modifying its parameters so that the
            // dynamically assigned port of the GCS Dev Service is preserved in the signed URL
            object : Storage by storage {
                override fun signUrl(
                    blobInfo: BlobInfo,
                    duration: Long,
                    unit: TimeUnit,
                    vararg options: Storage.SignUrlOption,
                ): URL {
                    val amendedOptions = options.toMutableList()
                    amendedOptions.add(Storage.SignUrlOption.withHostName(host))
                    return storage.signUrl(blobInfo, duration, unit, *amendedOptions.toTypedArray())
                }
            }
        } else {
            StorageOptions.getDefaultInstance().service
        }

        return storage
    }

    fun closeStorage(@Disposes storage: Storage) {
        storage.close()
    }
}

private fun generateDevCredentials(): ServiceAccountCredentials {
    val keyPair = KeyPairGenerator
        .getInstance("RSA")
        .apply { initialize(2048) }
        .generateKeyPair()
    val encodedPrivateKey = Base64
        .getEncoder()
        .encodeToString(keyPair.private.encoded)
    val privateKeyPkcs8 = """
        -----BEGIN PRIVATE KEY-----
        $encodedPrivateKey
        -----END PRIVATE KEY-----
    """.trimIndent()

    val credentials = ServiceAccountCredentials.fromPkcs8(
        "fake-gcs-client",
        "fake-gcs-client@example.com",
        privateKeyPkcs8,
        null,
        null,
    )

    return credentials
}
