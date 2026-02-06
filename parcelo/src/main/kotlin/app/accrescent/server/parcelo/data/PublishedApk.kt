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
    var appPackageId: UUID,

    @Column(columnDefinition = "text", name = "apk_path", nullable = false)
    var apkPath: String,

    @Column(columnDefinition = "text", name = "bucket_id", nullable = false)
    var bucketId: String,

    @Column(columnDefinition = "text", name = "object_id", nullable = false)
    var objectId: String,

    @Column(nullable = false)
    var size: ULong,
) : PanacheEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_package_id", insertable = false, updatable = false)
    private lateinit var appPackage: AppPackage

    companion object : PanacheCompanion<PublishedApk> {
        fun findByQualifiedPaths(appId: String, paths: List<String>): List<PublishedApk> {
            return find(
                "JOIN AppPackage app_packages " +
                        "ON app_packages.id = appPackageId " +
                        "JOIN App apps " +
                        "ON apps.id = app_packages.appId " +
                        "WHERE apps.id = ?1 " +
                        "AND apkPath IN ?2",
                appId,
                paths,
            )
                .list()
        }
    }
}
