package app.accrescent.parcelo.console.data

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object RejectionReasons : IntIdTable("rejection_reasons") {
    val reviewId = reference("review_id", Reviews, ReferenceOption.CASCADE)
    val reason = text("reason")
}

class RejectionReason(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RejectionReason>(RejectionReasons)

    var reviewId by RejectionReasons.reviewId
    var reason by RejectionReasons.reason
}
