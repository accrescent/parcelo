// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import app.accrescent.server.parcelo.config.ParceloConfig
import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import io.quarkus.runtime.annotations.RegisterForReflection
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.net.URI
import java.time.OffsetDateTime

enum class OidcProvider {
    LOCAL,
    UNKNOWN;

    companion object {
        fun fromIssuer(issuer: String): OidcProvider {
            return when {
                URI(issuer).host == "localhost" -> LOCAL
                else -> UNKNOWN
            }
        }

        fun fromConfig(provider: ParceloConfig.OidcProvider): OidcProvider {
            return when (provider) {
                ParceloConfig.OidcProvider.LOCAL -> LOCAL
            }
        }
    }
}

@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(columnNames = ["oidc_issuer", "oidc_subject"])],
)
class User(
    @Id
    @Column(columnDefinition = "text")
    var id: String,

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "text", name = "oidc_provider", nullable = false)
    var oidcProvider: OidcProvider,

    @Column(columnDefinition = "text", name = "oidc_issuer", nullable = false)
    var oidcIssuer: String,

    @Column(columnDefinition = "text", name = "oidc_subject", nullable = false)
    var oidcSubject: String,

    @Column(columnDefinition = "text", nullable = false)
    var email: String,

    @Column(nullable = false)
    var reviewer: Boolean,

    @Column(nullable = false)
    var publisher: Boolean,

    @Column(name = "registered_at", nullable = false)
    var registeredAt: OffsetDateTime,
) : PanacheEntityBase {
    companion object : PanacheCompanionBase<User, String> {
        fun existsById(id: String): Boolean {
            return count("WHERE id = ?1", id) > 0
        }

        fun existsByOidcId(issuer: String, subject: String): Boolean {
            return count("WHERE oidcIssuer = ?1 AND oidcSubject = ?2", issuer, subject) > 0
        }

        fun findIdByOidcId(issuer: String, subject: String): UserId? {
            return find("WHERE oidcIssuer = ?1 AND oidcSubject = ?2", issuer, subject)
                .project(UserId::class.java)
                .firstResult()
        }

        fun findRandomReviewer(): User? {
            return find("WHERE reviewer = true ORDER BY random() LIMIT 1").firstResult()
        }

        fun findRandomPublisher(): User? {
            return find("WHERE publisher = true ORDER BY random() LIMIT 1").firstResult()
        }

        fun countRegisteredSince(timestamp: OffsetDateTime): Long {
            return count("WHERE registeredAt >= ?1", timestamp)
        }
    }
}

@RegisterForReflection
data class UserId(val id: String)
