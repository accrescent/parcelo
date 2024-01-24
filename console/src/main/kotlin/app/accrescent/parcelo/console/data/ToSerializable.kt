// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.data

/**
 * Utility interface to convert database objects into serializable objects
 *
 * T must be a class annotated with @Serializable
 */
fun interface ToSerializable<T> {
    /**
     * Returns a serializable representation of the database object
     */
    fun serializable(): T
}
