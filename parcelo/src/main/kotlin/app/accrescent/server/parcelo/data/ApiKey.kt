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

@Entity
@Table(name = "api_keys")
class ApiKey(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(columnDefinition = "text", name = "api_key_hash", nullable = false, unique = true)
    val apiKeyHash: String,
) : PanacheEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(insertable = false, updatable = false)
    lateinit var user: User
}
