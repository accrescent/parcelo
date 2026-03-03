// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package android.net

open class ConnectivityManager {
    open fun getActiveNetwork(): Network? = throw NotImplementedError()
    open fun getLinkProperties(network: Network?): LinkProperties? = throw NotImplementedError()
}
