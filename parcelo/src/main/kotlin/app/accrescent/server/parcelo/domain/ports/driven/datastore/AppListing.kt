// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driven.datastore

import app.accrescent.server.parcelo.core.text.UString
import app.accrescent.server.parcelo.domain.appstore.ListingLanguage

data class AppListing(val id: UString, val appId: UString, val language: ListingLanguage)
