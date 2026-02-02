// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "app_edit_acls",
    uniqueConstraints = [UniqueConstraint(columnNames = ["app_edit_id", "user_id"])],
)
class AppEditAcl(
    @Column(columnDefinition = "text", name = "app_edit_id", nullable = false)
    val appEditId: String,

    @Column(columnDefinition = "text", name = "user_id", nullable = false)
    val userId: String,

    @Column(name = "can_review", nullable = false)
    var canReview: Boolean,
) : PanacheEntity() {
    companion object : PanacheCompanion<AppEditAcl> {
        fun findByAppEditIdAndUserId(appEditId: String, userId: String): AppEditAcl? {
            return find("WHERE appEditId = ?1 AND userId = ?2", appEditId, userId).firstResult()
        }
    }
}
