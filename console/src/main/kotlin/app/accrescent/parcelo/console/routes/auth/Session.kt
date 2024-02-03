// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.routes.auth

import io.ktor.server.auth.Principal

/**
 * An authenticated session
 *
 * @property id the session ID
 */
data class Session(val id: String) : Principal
