// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.routes.auth

import kotlinx.serialization.Serializable

/**
 * An authenticated session
 *
 * @property id the session ID
 */
@Serializable
data class Session(val id: String)
