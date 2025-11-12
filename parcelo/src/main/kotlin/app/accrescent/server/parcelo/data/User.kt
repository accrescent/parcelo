// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntity
import io.quarkus.runtime.annotations.RegisterForReflection
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(columnNames = ["identity_provider", "scoped_user_id"])],
)
class User(
    @Column(columnDefinition = "text", name = "scoped_user_id", nullable = false)
    val scopedUserId: String,
) : PanacheEntity() {
    @Column(columnDefinition = "text", name = "identity_provider", nullable = false)
    val identityProvider = "github"

    companion object : PanacheCompanion<User> {
        fun findByApiKeyHash(hash: String): User? {
            return find(
                "SELECT api_keys.user " +
                        "FROM ApiKey api_keys " +
                        "WHERE api_keys.apiKeyHash = ?1",
                hash,
            )
                .firstResult()
        }

        fun findIdByGithubUserId(userId: String): UserId? {
            return find("WHERE identityProvider = 'github' AND scopedUserId = ?1", userId)
                .project(UserId::class.java)
                .firstResult()
        }
    }
}

@RegisterForReflection
data class UserId(val id: Long)
