// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

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
    val id: UUID,

    @Column(name = "app_edit_id", nullable = false)
    val appEditId: UUID,

    @Column(columnDefinition = "text", nullable = false)
    val language: String,

    @Column(columnDefinition = "text", nullable = false)
    val name: String,

    @Column(columnDefinition = "text", name = "short_description", nullable = false)
    val shortDescription: String,

    @Column(name = "icon_image_id", nullable = false)
    var iconImageId: UUID,
) : PanacheEntityBase {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_edit_id", insertable = false, updatable = false)
    lateinit var appEdit: AppEdit

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "icon_image_id", insertable = false, updatable = false)
    lateinit var icon: Image
}
