// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driving.console

enum class ListingLanguage {
    EN_US;

    override fun toString(): String {
        return when (this) {
            EN_US -> "en-US"
        }
    }
}
