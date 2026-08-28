// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driving.console

import app.accrescent.server.parcelo.core.text.UString
import app.accrescent.server.parcelo.domain.appstore.ListingLanguage

data class CreateAppDraftListingRequest(
    val appDraftId: UString,
    val language: ListingLanguage,
    val name: UString,
    val shortDescription: UString,
)
