// SPDX-FileCopyrightText: © 2026 Logan Magee
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
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.util.UUID

@Entity
@Table(
    name = "app_package_permissions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["app_package_id", "name"])],
)
class AppPackagePermission(
    @Column(name = "app_package_id", nullable = false)
    var appPackageId: UUID,

    @Column(columnDefinition = "text", nullable = false)
    var name: String,

    @Column(name = "max_sdk_version")
    var maxSdkVersion: Int?,
) : PanacheEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_package_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    lateinit var appPackage: AppPackage
}
