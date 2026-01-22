// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import io.quarkus.runtime.annotations.RegisterForReflection
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(columnNames = ["identity_provider", "scoped_user_id"])],
)
class User(
    @Id
    @Column(columnDefinition = "text")
    val id: String,

    @Column(columnDefinition = "text", name = "scoped_user_id", nullable = false)
    val scopedUserId: String,
) : PanacheEntityBase {
    @Column(columnDefinition = "text", name = "identity_provider", nullable = false)
    val identityProvider = "github"

    companion object : PanacheCompanionBase<User, String> {
        fun existsById(id: String): Boolean {
            return count("WHERE id = ?1", id) > 0
        }

        fun existsByGithubUserId(userId: String): Boolean {
            return count("WHERE identityProvider = 'github' AND scopedUserId = ?1", userId) > 0
        }

        fun findIdByGithubUserId(userId: String): UserId? {
            return find("WHERE identityProvider = 'github' AND scopedUserId = ?1", userId)
                .project(UserId::class.java)
                .firstResult()
        }
    }
}

@RegisterForReflection
data class UserId(val id: String)
