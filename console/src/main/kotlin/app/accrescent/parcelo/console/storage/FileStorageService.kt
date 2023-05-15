package app.accrescent.parcelo.console.storage

import org.jetbrains.exposed.dao.id.EntityID
import java.io.InputStream

interface FileStorageService {
    fun saveFile(inputStream: InputStream): EntityID<Int>
    fun deleteFile(id: EntityID<Int>)
    fun loadFile(id: EntityID<Int>): InputStream
}
