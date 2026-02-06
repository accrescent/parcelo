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
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

@Entity
@Table(name = "publishers")
class Publisher(
    @Column(columnDefinition = "text", name = "user_id", nullable = false, unique = true)
    var userId: String,

    @Column(columnDefinition = "text", nullable = false)
    var email: String,
) : PanacheEntity() {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    lateinit var user: User

    companion object : PanacheCompanion<Publisher> {
        fun existsByUserId(userId: String): Boolean {
            return count("WHERE userId = ?1", userId) > 0
        }

        fun findRandom(): Publisher? {
            return find("ORDER BY random() LIMIT 1").firstResult()
        }
    }
}
