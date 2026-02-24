// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.security

import java.net.InetAddress

sealed class Principal {
    data class User(val userId: String) : Principal()
    data class IpAddress(val address: InetAddress) : Principal()
}
