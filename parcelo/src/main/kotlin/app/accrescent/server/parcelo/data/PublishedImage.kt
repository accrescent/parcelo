// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "published_images",
    uniqueConstraints = [UniqueConstraint(columnNames = ["bucket_id", "object_id"])],
)
class PublishedImage(
    @Id
    val id: UUID,

    @Column(name = "bucket_id", nullable = false)
    val bucketId: String,

    @Column(name = "object_id", nullable = false)
    val objectId: String,
) : PanacheEntityBase
