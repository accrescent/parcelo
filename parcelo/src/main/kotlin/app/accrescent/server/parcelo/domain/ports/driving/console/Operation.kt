// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driving.console

sealed class Operation<out T, out E> {
    abstract val id: String

    data class Incomplete(override val id: String) : Operation<Nothing, Nothing>()
    data class Succeeded<T>(override val id: String, val response: T) : Operation<T, Nothing>()
    data class Failed<E>(override val id: String, val error: E) : Operation<Nothing, E>()
}
