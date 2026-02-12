// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.testutil

import com.google.rpc.ErrorInfo
import com.google.rpc.Status

fun Status.errorInfo(): ErrorInfo? {
    return detailsList
        .firstOrNull { it.`is`(ErrorInfo::class.java) }
        ?.unpack(ErrorInfo::class.java)
}
