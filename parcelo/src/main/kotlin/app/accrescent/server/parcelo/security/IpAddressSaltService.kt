// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.server.parcelo.data.IpAddressSalt
import io.quarkus.logging.Log
import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.enterprise.context.ApplicationScoped
import java.security.SecureRandom
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val SALT_SIZE_BYTES = 16

@ApplicationScoped
class IpAddressSaltService {
    @Volatile
    private var cachedSalt: CachedSalt? = null
    private val cacheLock = ReentrantLock()

    private companion object {
        private val SALT_LIFETIME = Duration.ofDays(1)
        private val secureRandom = SecureRandom()
    }

    fun getCurrentSalt(): ByteArray {
        val now = OffsetDateTime.now()
        val cached = cachedSalt

        // Return cached salt if it's still valid
        if (cached != null && now.isBefore(cached.expiresAt)) {
            return cached.salt
        }

        return cacheLock.withLock {
            // Double check after acquiring lock
            //
            // https://en.wikipedia.org/wiki/Double-checked_locking#Usage_in_Java
            val cached = cachedSalt
            if (cached != null && now.isBefore(cached.expiresAt)) {
                return cached.salt
            }

            val salt = QuarkusTransaction.joiningExisting().call {
                val currentSalt = IpAddressSalt.getCurrent()
                when {
                    currentSalt == null -> {
                        Log.info("No IP address salt found in database, generating new salt")
                        generateNewSalt(now).also { it.persist() }
                    }

                    !now.isBefore(currentSalt.expiresAt) -> {
                        Log.info("IP address salt expired at ${currentSalt.expiresAt}, rotating")
                        currentSalt.delete()
                        generateNewSalt(now).also { it.persist() }
                    }

                    else -> currentSalt
                }
            }

            cachedSalt = CachedSalt(
                salt = salt.currentSalt,
                expiresAt = salt.expiresAt,
            )

            salt.currentSalt
        }
    }

    fun rotateIfExpired() {
        val now = OffsetDateTime.now()

        return QuarkusTransaction.joiningExisting().call {
            val currentSalt = IpAddressSalt.getCurrent() ?: run {
                Log.info("No IP address salt found during rotation, generating new salt")
                generateNewSalt(now).also { it.persist() }
            }

            if (now.isBefore(currentSalt.expiresAt)) {
                // No need to rotate - the salt hasn't expired yet
                return@call
            }

            currentSalt.delete()
            val newSalt = generateNewSalt(now).also { it.persist() }

            Log.info("Rotated IP address salt, new expiration at ${newSalt.expiresAt}")
        }
    }

    private fun generateNewSalt(now: OffsetDateTime): IpAddressSalt {
        return IpAddressSalt(
            id = true,
            currentSalt = ByteArray(SALT_SIZE_BYTES).also { secureRandom.nextBytes(it) },
            expiresAt = now.plus(SALT_LIFETIME),
            createdAt = now,
        )
    }

    private class CachedSalt(
        val salt: ByteArray,
        val expiresAt: OffsetDateTime,
    )
}
