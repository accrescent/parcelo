package app.accrescent.parcelo.storage

import org.jetbrains.exposed.dao.id.EntityID
import java.io.InputStream

interface FileStorageService {
    fun saveFile(inputStream: InputStream): EntityID<Int>
    fun deleteFile(id: EntityID<Int>)
}
