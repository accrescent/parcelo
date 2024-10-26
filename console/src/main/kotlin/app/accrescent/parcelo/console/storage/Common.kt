// Copyright 2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.storage

import app.accrescent.parcelo.console.data.File
import app.accrescent.parcelo.console.data.Files
import app.accrescent.parcelo.console.data.Files.deleted
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.not
import java.util.UUID

/**
 * Finds the given non-deleted file
 *
 * If the file is marked deleted, returns null.
 *
 * Note: This function requires being wrapped in a transaction context.
 */
internal fun findFile(id: Int): File? {
    return File.find { Files.id eq id and not(deleted) }.singleOrNull()
}

/**
 * Generates a unique object ID for new objects
 *
 * @return a new, unique object ID
 */
internal fun generateObjectId(): String {
    return UUID.randomUUID().toString()
}
