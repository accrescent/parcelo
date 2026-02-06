// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "app_edit_listings",
    uniqueConstraints = [UniqueConstraint(columnNames = ["app_edit_id", "language"])],
)
class AppEditListing(
    @Id
    var id: UUID,

    @Column(columnDefinition = "text", name = "app_edit_id", nullable = false)
    var appEditId: String,

    @Column(columnDefinition = "text", nullable = false)
    var language: String,

    @Column(columnDefinition = "text", nullable = false)
    var name: String,

    @Column(columnDefinition = "text", name = "short_description", nullable = false)
    var shortDescription: String,

    @Column(name = "icon_image_id")
    var iconImageId: UUID?,
) : PanacheEntityBase {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_edit_id", insertable = false, updatable = false)
    lateinit var appEdit: AppEdit

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "icon_image_id", insertable = false, updatable = false)
    var icon: Image? = null

    companion object : PanacheCompanionBase<AppEditListing, UUID> {
        fun findByAppEditIdAndLanguage(appEditId: String, language: String): AppEditListing? {
            return find("WHERE appEditId = ?1 AND language = ?2", appEditId, language).firstResult()
        }

        fun exists(appEditId: String, language: String): Boolean {
            return count("WHERE appEditId = ?1 AND language = ?2", appEditId, language) > 0
        }
    }
}
