// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "published_images",
    uniqueConstraints = [UniqueConstraint(columnNames = ["bucket_id", "object_id"])],
)
class PublishedImage(
    @Column(name = "image_id", nullable = false)
    var imageId: UUID,

    @Column(columnDefinition = "text", name = "bucket_id", nullable = false)
    var bucketId: String,

    @Column(columnDefinition = "text", name = "object_id", nullable = false)
    var objectId: String,
) : PanacheEntity() {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "image_id", insertable = false, updatable = false)
    lateinit var icon: Image

    companion object : PanacheCompanion<PublishedImage> {
        fun findIconByAppIdAndListingLanguage(appId: String, language: String): PublishedImage? {
            return find(
                "JOIN AppListing app_listings " +
                        "ON app_listings.iconImageId = imageId " +
                        "WHERE app_listings.id.appId = ?1 " +
                        "AND app_listings.id.language = ?2",
                appId,
                language,
            )
                .firstResult()
        }
    }
}
