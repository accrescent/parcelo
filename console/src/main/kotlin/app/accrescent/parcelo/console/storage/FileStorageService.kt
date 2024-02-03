// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.storage

import org.jetbrains.exposed.dao.id.EntityID
import java.io.InputStream

/**
 * Abstraction of file storage
 */
interface FileStorageService {
    /**
     * Save a file to the file storage service
     *
     * @return the database ID of the new file
     */
    fun saveFile(inputStream: InputStream): EntityID<Int>

    /**
     * Deletes the given file by its ID
     */
    fun deleteFile(id: EntityID<Int>)

    /**
     * Load the file with the given ID
     *
     * @return the file's data stream
     */
    fun loadFile(id: EntityID<Int>): InputStream
}
