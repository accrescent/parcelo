// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driving.console

import app.accrescent.server.parcelo.core.text.UString
import arrow.core.Option

data class UpdateAppDraftListingRequest(
    val appDraftListingId: UString,
    val name: Option<UString>,
    val shortDescription: Option<UString>,
)
