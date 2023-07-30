package app.accrescent.parcelo.console.storage

import org.jetbrains.exposed.dao.id.EntityID
import java.io.InputStream

interface FileStorageService {
    fun saveFile(inputStream: InputStream): EntityID<Int>

    /**
     * Deletes the given file by its ID
     *
     * @return whether deleting the file from disk succeeded
     */
    fun deleteFile(id: EntityID<Int>): Boolean
    fun loadFile(id: EntityID<Int>): InputStream
}
