// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import app.accrescent.server.parcelo.model.UserId
import io.grpc.Context

object AuthnContextKey {
    val USER_ID: Context.Key<UserId> = Context.key("userId")
}
