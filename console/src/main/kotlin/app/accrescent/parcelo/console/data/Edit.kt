// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.data

import app.accrescent.parcelo.console.data.net.Edit as SerializableEdit
import app.accrescent.parcelo.console.data.net.EditStatus
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import java.util.UUID

object Edits : UUIDTable("edits") {
    val appId = reference("app_id", Apps, ReferenceOption.CASCADE)
    val shortDescription = text("short_description").nullable()
    val creationTime = long("creation_time").clientDefault { System.currentTimeMillis() / 1000 }

    init {
        check {
            // At least one metadata field must be non-null
            shortDescription.isNotNull()
        }
    }
}

class Edit(id: EntityID<UUID>) : UUIDEntity(id), ToSerializable<SerializableEdit> {
    companion object : UUIDEntityClass<Edit>(Edits)

    var appId by Edits.appId
    var shortDescription by Edits.shortDescription
    val creationTime by Edits.creationTime

    override fun serializable(): SerializableEdit {
        return SerializableEdit(
            id.value.toString(),
            appId.value,
            shortDescription,
            creationTime,
            EditStatus.UNSUBMITTED,
        )
    }
}
