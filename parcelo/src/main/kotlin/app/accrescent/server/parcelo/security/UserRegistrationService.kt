// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.server.parcelo.config.ParceloConfig
import app.accrescent.server.parcelo.data.User
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.OffsetDateTime
import kotlin.jvm.optionals.getOrNull

@ApplicationScoped
class UserRegistrationService(private val config: ParceloConfig) {
    @Transactional(Transactional.TxType.MANDATORY)
    fun registrationsAvailable(): Boolean {
        val limit = config.userRegistration().map { it.limit() }.getOrNull() ?: return false

        val cutoff = OffsetDateTime.now() - limit.period()
        val usersRegisteredInPeriod = User.countRegisteredSince(cutoff)

        return usersRegisteredInPeriod < limit.registrations()
    }
}
