// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driving.console

/**
 * The context for an API call.
 *
 * @property userId the ID of the authenticated user who is making the API call.
 */
data class CallContext(val userId: String)
