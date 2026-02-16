// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

private const val PERIOD = "15m"

/**
 * Rotates expired IP address salts periodically.
 *
 * This job is not necessary for accurate user differentiation since
 * [IpAddressSaltService.getCurrentSalt] lazily rotates IP address salts when appropriate. This job
 * _is_ necessary, however, to ensure that salts are rotated proactively (i.e., even if no requests
 * are received which trigger [IpAddressSaltService.getCurrentSalt]) so that user IP addresses are
 * anonymized within the expected time frame.
 */
@ApplicationScoped
class IpAddressSaltRotationJob @Inject constructor(
    private val saltService: IpAddressSaltService,
) {
    @Scheduled(every = PERIOD)
    fun rotateSaltIfNecessary() {
        saltService.rotateIfExpired()
    }
}
