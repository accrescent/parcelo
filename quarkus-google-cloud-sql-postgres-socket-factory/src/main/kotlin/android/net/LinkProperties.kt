// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package android.net

import java.net.InetAddress

class LinkProperties {
    fun getDnsServers(): MutableList<InetAddress> = throw NotImplementedError()
    fun getDomains(): String = throw NotImplementedError()
}
