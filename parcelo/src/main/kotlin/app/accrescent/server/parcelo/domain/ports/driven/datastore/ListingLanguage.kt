// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driven.datastore

import arrow.core.None
import arrow.core.Option
import arrow.core.Some

enum class ListingLanguage {
    EN_US;

    override fun toString() = when (this) {
        EN_US -> "en-US"
    }

    companion object {
        fun fromString(s: String): Option<ListingLanguage> {
            return when (s) {
                "en-US" -> Some(EN_US)
                else -> None
            }
        }
    }
}
