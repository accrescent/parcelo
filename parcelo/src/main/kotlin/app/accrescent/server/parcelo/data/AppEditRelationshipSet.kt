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
    name = "app_edit_relationship_sets",
    uniqueConstraints = [UniqueConstraint(columnNames = ["app_edit_id", "user_id"])],
)
class AppEditRelationshipSet(
    @Column(columnDefinition = "text", name = "app_edit_id", nullable = false)
    var appEditId: String,

    @Column(columnDefinition = "text", name = "user_id", nullable = false)
    var userId: String,

    @Column(nullable = false)
    var reviewer: Boolean,
) : PanacheEntity() {
    companion object : PanacheCompanion<AppEditRelationshipSet> {
        fun findByAppEditIdAndUserId(appEditId: String, userId: String): AppEditRelationshipSet? {
            return find("WHERE appEditId = ?1 AND userId = ?2", appEditId, userId).firstResult()
        }

        fun findByAppEditListingIdAndUserId(
            appEditListingId: String,
            userId: String,
        ): AppEditRelationshipSet? {
            return find(
                "FROM AppEditRelationshipSet app_edit_relationship_sets " +
                        "JOIN AppEdit app_edits " +
                        "ON app_edits.id = app_edit_relationship_sets.appEditId " +
                        "JOIN AppEditListing app_edit_listings " +
                        "ON app_edit_listings.appEditId = app_edits.id " +
                        "WHERE app_edit_listings.id = ?1 " +
                        "AND app_edit_relationship_sets.userId = ?2",
                appEditListingId,
                userId,
            )
                .firstResult()
        }
    }
}
