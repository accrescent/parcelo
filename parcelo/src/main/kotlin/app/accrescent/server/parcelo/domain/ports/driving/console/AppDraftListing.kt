// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driving.console

import app.accrescent.server.parcelo.core.text.UString

data class AppDraftListing(
    val id: UString,
    val appDraftId: UString,
    val language: UString,
    val name: UString,
    val shortDescription: UString,
)
