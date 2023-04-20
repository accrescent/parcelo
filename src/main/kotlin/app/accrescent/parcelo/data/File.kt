package app.accrescent.parcelo.data

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object Files : IntIdTable("files") {
    val localPath = text("local_path")
}

class File(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<File>(Files)

    var localPath by Files.localPath
}
