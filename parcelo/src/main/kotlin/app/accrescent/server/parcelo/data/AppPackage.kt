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
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(
    name = "app_packages",
    uniqueConstraints = [UniqueConstraint(columnNames = ["bucket_id", "object_id"])],
)
class AppPackage(
    @Id
    val id: UUID,

    @Column(columnDefinition = "text", name = "bucket_id", nullable = false)
    var bucketId: String,

    @Column(columnDefinition = "text", name = "object_id", nullable = false)
    var objectId: String,

    // This field must be a var instead of a val so that Hibernate can properly populate it from the
    // database
    @Column(name = "upload_pub_sub_event_time", nullable = false)
    var uploadPubSubEventTime: OffsetDateTime,

    @Column(columnDefinition = "text", name = "app_id", nullable = false)
    var appId: String,

    @Column(name = "version_code", nullable = false)
    val versionCode: Int,

    @Column(columnDefinition = "text", name = "version_name", nullable = false)
    val versionName: String,

    @Column(name = "target_sdk", nullable = false)
    val targetSdk: Int,

    @Column(name = "signing_certificate", nullable = false)
    val signingCertificate: ByteArray,

    @Column(name = "build_apks_result", nullable = false)
    val buildApksResult: ByteArray,
) : PanacheEntityBase
