// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntity
import io.quarkus.runtime.annotations.RegisterForReflection
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "app_listings",
    uniqueConstraints = [UniqueConstraint(columnNames = ["app_id", "language"])],
)
class AppListing(
    @Column(columnDefinition = "text", name = "app_id", nullable = false)
    val appId: String,

    @Column(columnDefinition = "text", nullable = false)
    val language: String,

    @Column(columnDefinition = "text", nullable = false)
    val name: String,

    @Column(columnDefinition = "text", name = "short_description", nullable = false)
    val shortDescription: String,

    @Column(name = "icon_image_id", nullable = false)
    val iconImageId: UUID,
) : PanacheEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(insertable = false, updatable = false)
    lateinit var app: App

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "icon_image_id", insertable = false, updatable = false)
    lateinit var icon: Image

    companion object : PanacheCompanion<AppListing> {
        fun findByAppIdAndLanguage(appId: String, language: String): AppListing? {
            return find("WHERE appId = ?1 AND language = ?2", appId, language).firstResult()
        }

        fun findByIdsOrdered(ids: List<Pair<String, String>>): List<AppListing> {
            return find(
                "WHERE (appId, language) IN ?1 ORDER BY appId ASC",
                ids.map { arrayOf(it.first, it.second) },
            )
                .list()
        }

        fun findIdsForApps(appIds: Set<String>): List<ListingId> {
            return find("WHERE appId IN ?1", appIds).project(ListingId::class.java).list()
        }

        fun getListingLanguagesForApp(appId: String): List<ListingLanguage> {
            return find("WHERE appId = ?1", appId).project(ListingLanguage::class.java).list()
        }
    }
}

@RegisterForReflection
data class ListingId(val appId: String, val language: String)

@RegisterForReflection
data class ListingLanguage(val language: String)
