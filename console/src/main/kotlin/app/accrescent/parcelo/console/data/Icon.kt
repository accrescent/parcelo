package app.accrescent.parcelo.console.data

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object Icons : IntIdTable("icons") {
    val hash = text("hash")
    val fileId = reference("file_id", Files, ReferenceOption.NO_ACTION)
}

class Icon(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Icon>(Icons)

    var hash by Icons.hash
    var fileId by Icons.fileId
}
