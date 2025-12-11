// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntity
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
    name = "published_apks",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["app_package_id", "apk_path"]),
        UniqueConstraint(columnNames = ["bucket_id", "object_id"]),
    ]
)
class PublishedApk(
    @Column(name = "app_package_id", nullable = false)
    val appPackageId: UUID,

    @Column(columnDefinition = "text", name = "apk_path", nullable = false)
    val apkPath: String,

    @Column(columnDefinition = "text", name = "bucket_id", nullable = false)
    val bucketId: String,

    @Column(columnDefinition = "text", name = "object_id", nullable = false)
    val objectId: String,

    @Column(nullable = false)
    val size: ULong,
) : PanacheEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_package_id", insertable = false, updatable = false)
    private lateinit var appPackage: AppPackage
}
