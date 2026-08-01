// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driven.datastore

import java.time.OffsetDateTime

data class Organization(val id: String, val createTime: OffsetDateTime)
