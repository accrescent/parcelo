// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package android.content

abstract class Context {
    fun <T> getSystemService(serviceClass: Class<T>): T = throw NotImplementedError()
}
