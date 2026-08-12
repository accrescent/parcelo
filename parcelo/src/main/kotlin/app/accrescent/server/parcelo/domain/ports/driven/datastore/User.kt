// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driven.datastore

import java.time.OffsetDateTime

/**
 * A user of the console.
 *
 * @property id the user's unique ID.
 * @property organizationId the ID of the organization this user exclusively owns.
 * @property createTime the time at which the user was created.
 */
data class User(val id: String, val organizationId: String, val createTime: OffsetDateTime)
