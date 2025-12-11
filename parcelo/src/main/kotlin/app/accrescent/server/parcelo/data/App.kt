// SPDX-FileCopyrightText: © 2025 Logan Magee
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
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "apps")
class App(
    @Id
    @Column(columnDefinition = "text")
    val id: String,

    @Column(columnDefinition = "text", name = "default_listing_language", nullable = false)
    val defaultListingLanguage: String,

    @Column(name = "app_package_id", nullable = false)
    val appPackageId: UUID,
) : PanacheEntityBase {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_package_id", insertable = false, updatable = false)
    lateinit var appPackage: AppPackage

    companion object : PanacheCompanionBase<App, UUID> {
        fun existsById(id: String): Boolean {
            return count("WHERE id = ?1", id) > 0
        }
    }
}
