// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import io.quarkus.runtime.annotations.RegisterForReflection
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(
    name = "app_packages",
    uniqueConstraints = [UniqueConstraint(columnNames = ["bucket_id", "object_id"])],
)
class AppPackage(
    @Id
    var id: UUID,

    @Column(columnDefinition = "text", name = "bucket_id", nullable = false)
    var bucketId: String,

    @Column(columnDefinition = "text", name = "object_id", nullable = false)
    var objectId: String,

    @Column(name = "upload_pub_sub_event_time", nullable = false)
    var uploadPubSubEventTime: OffsetDateTime,

    @Column(columnDefinition = "text", name = "app_id", nullable = false)
    var appId: String,

    @Column(name = "version_code", nullable = false)
    var versionCode: Int,

    @Column(columnDefinition = "text", name = "version_name", nullable = false)
    var versionName: String,

    @Column(name = "target_sdk", nullable = false)
    var targetSdk: Int,

    @Column(name = "signing_certificate", nullable = false)
    var signingCertificate: ByteArray,

    @Column(name = "build_apks_result", nullable = false)
    var buildApksResult: ByteArray,
) : PanacheEntityBase {
    @OneToMany(cascade = [CascadeType.ALL], mappedBy = "appPackage")
    lateinit var publishedApks: List<PublishedApk>

    @OneToMany(cascade = [CascadeType.ALL], mappedBy = "appPackage")
    lateinit var permissions: Set<AppPackagePermission>

    companion object : PanacheCompanionBase<AppPackage, UUID> {
        fun findByPublishedAppId(appId: String): AppPackage? {
            return find(
                "FROM AppPackage app_packages " +
                        "JOIN App apps " +
                        "ON apps.id = app_packages.appId " +
                        "WHERE app_packages.appId = ?1",
                appId,
            )
                .firstResult()
        }

        fun findPackageInfoByPublishedAppId(appId: String): AppPackageInfo? {
            return find(
                "FROM AppPackage app_packages " +
                        "JOIN App apps " +
                        "ON apps.id = app_packages.appId " +
                        "WHERE app_packages.appId = ?1",
                appId,
            )
                .project(AppPackageInfo::class.java)
                .firstResult()
        }
    }
}

@RegisterForReflection
data class AppPackageInfo(val versionCode: Int, val versionName: String)
