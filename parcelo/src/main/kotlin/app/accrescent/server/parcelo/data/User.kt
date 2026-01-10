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
import java.util.UUID

@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(columnNames = ["identity_provider", "scoped_user_id"])],
)
class User(
    @Id
    val id: UUID,

    @Column(columnDefinition = "text", name = "scoped_user_id", nullable = false)
    val scopedUserId: String,
) : PanacheEntityBase {
    @Column(columnDefinition = "text", name = "identity_provider", nullable = false)
    val identityProvider = "github"

    companion object : PanacheCompanionBase<User, UUID> {
        fun existsById(id: UUID): Boolean {
            return count("WHERE id = ?1", id) > 0
        }

        fun findIdByApiKeyHash(hash: String): UserId? {
            return find(
                "SELECT users.id " +
                        "FROM User users " +
                        "JOIN ApiKey api_keys " +
                        "ON api_keys.userId = users.id " +
                        "WHERE api_keys.apiKeyHash = ?1",
                hash,
            )
                .project(UserId::class.java)
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
data class UserId(val id: UUID)
