// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import io.quarkus.runtime.annotations.RegisterForReflection
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
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
    @Id
    @Column(columnDefinition = "text")
    var id: String,

    @Column(columnDefinition = "text", name = "app_id", nullable = false)
    var appId: String,

    @Column(columnDefinition = "text", nullable = false)
    var language: String,

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

    companion object : PanacheCompanionBase<AppListing, String> {
        fun findByAppIdAndLanguage(appId: String, language: String): AppListing? {
            return find("WHERE appId = ?1 AND language = ?2", appId, language).firstResult()
        }

        fun findByAppIdsAndLanguagesOrdered(ids: List<Pair<String, String>>): List<AppListing> {
            return find(
                "WHERE id IN ?1 ORDER BY id ASC",
                ids.map { arrayOf(it.first, it.second) },
            )
                .list()
        }

        fun findAppIdsAndLanguagesForApps(appIds: Set<String>): List<AppIdAndLanguage> {
            return find(
                "SELECT app_listings.appId, app_listings.language " +
                        "FROM AppListing app_listings " +
                        "WHERE app_listings.appId IN ?1",
                appIds,
            )
                .project(AppIdAndLanguage::class.java)
                .list()
        }

        fun getListingLanguagesForApp(appId: String): List<ListingLanguage> {
            return find(
                "SELECT app_listings.language " +
                        "FROM AppListing app_listings " +
                        "WHERE app_listings.appId = ?1",
                appId,
            )
                .project(ListingLanguage::class.java)
                .list()
        }
    }
}

@RegisterForReflection
data class AppIdAndLanguage(val appId: String, val language: String)

@RegisterForReflection
data class ListingLanguage(val language: String)
