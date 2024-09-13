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
    suspend fun saveFile(inputStream: InputStream): EntityID<Int>

    /**
     * Marks the given file deleted
     *
     * This method does not guarantee that the file has been deleted from the underlying storage
     * medium. However, all future calls to [loadFile] for the same file with throw
     * [NoSuchFileException], and the file should be considered deleted for all purposes besides
     * cleaning.
     */
    suspend fun markDeleted(id: Int)

    /**
     * Delete the given file from persistent storage
     *
     * The file must be previously marked deleted by [markDeleted], otherwise this function does
     * nothing.
     */
    suspend fun cleanFile(id: Int)

    /**
     * Deletes all files marked deleted from persistent storage
     */
    suspend fun cleanAllFiles()

    /**
     * Load the file with the given ID
     *
     * @return the file's data stream
     */
    suspend fun <T> loadFile(id: EntityID<Int>, block: suspend (InputStream) -> T): T
}
