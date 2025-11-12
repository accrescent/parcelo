// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import io.grpc.Context
import java.util.UUID

object AuthnContextKey {
    val USER_ID: Context.Key<UUID> = Context.key("userId")
}
