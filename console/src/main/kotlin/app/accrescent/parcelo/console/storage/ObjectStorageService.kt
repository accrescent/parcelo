// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.storage

import org.jetbrains.exposed.dao.id.EntityID
import java.io.InputStream
import java.nio.file.Path

/**
 * Abstraction of object storage
 */
interface ObjectStorageService {
    /**
     * Upload an object from a file to the object storage service
     *
     * @param path the path of the file to upload
     * @return the database ID of the new object
     */
    suspend fun uploadFile(path: Path): EntityID<Int>

    /**
     * Upload an object from memory to the object storage service
     *
     * @param bytes the object data to upload
     * @return the database ID of the new object
     */
    suspend fun uploadBytes(bytes: ByteArray): EntityID<Int>

    /**
     * Marks the given file deleted
     *
     * This method does not guarantee that the file has been deleted from the underlying storage
     * medium. However, all future calls to [loadObject] for the same file with throw
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
    suspend fun cleanObject(id: Int)

    /**
     * Deletes all files marked deleted from persistent storage
     */
    suspend fun cleanAllObjects()

    /**
     * Load the file with the given ID
     *
     * @return the file's data stream
     */
    suspend fun <T> loadObject(id: EntityID<Int>, block: suspend (InputStream) -> T): T
}
