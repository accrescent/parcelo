// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driven.datastore

import app.accrescent.server.parcelo.core.text.UString

data class App(
    val id: UString,
    val organizationId: UString,
    val defaultAppListingId: UString,
    val publiclyListed: Boolean,
)
