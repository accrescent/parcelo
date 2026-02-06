// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import io.quarkus.runtime.annotations.RegisterForReflection
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "app_listings")
class AppListing(
    @EmbeddedId
    val id: ListingId,

    @Column(columnDefinition = "text", nullable = false)
    var name: String,

    @Column(columnDefinition = "text", name = "short_description", nullable = false)
    var shortDescription: String,

    @Column(name = "icon_image_id", nullable = false)
    var iconImageId: UUID,
) : PanacheEntityBase {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(insertable = false, updatable = false)
    lateinit var app: App

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "icon_image_id", insertable = false, updatable = false)
    lateinit var icon: Image

    companion object : PanacheCompanion<AppListing> {
        fun findByAppIdAndLanguage(appId: String, language: String): AppListing? {
            return find("id", ListingId(appId, language)).firstResult()
        }

        fun findByIdsOrdered(ids: List<Pair<String, String>>): List<AppListing> {
            return find(
                "WHERE id IN ?1 ORDER BY id ASC",
                ids.map { ListingId(it.first, it.second) },
            )
                .list()
        }

        fun findIdsForApps(appIds: Set<String>): List<ListingId> {
            return find(
                "SELECT app_listings.id.appId, app_listings.id.language " +
                        "FROM AppListing app_listings " +
                        "WHERE app_listings.id.appId IN ?1",
                appIds,
            )
                .project(ListingId::class.java)
                .list()
        }

        fun getListingLanguagesForApp(appId: String): List<ListingLanguage> {
            return find(
                "SELECT app_listings.id.language " +
                        "FROM AppListing app_listings " +
                        "WHERE app_listings.id.appId = ?1",
                appId,
            )
                .project(ListingLanguage::class.java)
                .list()
        }
    }
}

@Embeddable
data class ListingId(
    @Column(columnDefinition = "text", name = "app_id", nullable = false)
    val appId: String,

    @Column(columnDefinition = "text", nullable = false)
    val language: String,
)

@RegisterForReflection
data class ListingLanguage(val language: String)
