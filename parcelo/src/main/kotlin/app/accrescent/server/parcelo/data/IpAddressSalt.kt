// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.CheckConstraint
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(
    name = "ip_address_salts",
    // Forbid more than one row from existing at a time.
    check = [CheckConstraint(constraint = "id = true")],
)
class IpAddressSalt(
    @Id
    @Column(nullable = false)
    var id: Boolean,

    @Column(columnDefinition = "bytea", name = "current_salt", nullable = false)
    var currentSalt: ByteArray,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: OffsetDateTime,

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime,
) : PanacheEntityBase {
    companion object : PanacheCompanionBase<IpAddressSalt, Boolean> {
        fun getCurrent(): IpAddressSalt? {
            return findById(true)
        }
    }
}
