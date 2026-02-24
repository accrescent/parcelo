// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import java.time.Duration

sealed class RateLimitResult {
    data object Allowed : RateLimitResult()
    data class LimitExceeded(val retryDelay: Duration) : RateLimitResult()
}
