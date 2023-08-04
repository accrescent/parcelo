// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.routes.auth

import io.ktor.server.auth.Principal

data class Session(val id: String) : Principal
