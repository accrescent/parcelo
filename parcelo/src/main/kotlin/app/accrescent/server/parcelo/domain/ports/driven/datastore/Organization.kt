// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driven.datastore

import java.time.OffsetDateTime

/**
 * An organization console resources are grouped under.
 *
 * @property id the organization's unique ID.
 * @property ownerUserId the ID of the user who exclusively owns this organization.
 * @property createTime the time at which the organization was created.
 */
data class Organization(val id: String, val ownerUserId: String, val createTime: OffsetDateTime)
