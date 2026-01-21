// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.data

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.OffsetDateTime

enum class BackgroundJobType {
    PUBLISH_APP_DRAFT,
}

@Entity
@Table(name = "background_jobs")
class BackgroundJob(
    @Column(columnDefinition = "text", nullable = false)
    @Enumerated(EnumType.STRING)
    val type: BackgroundJobType,

    @Column(columnDefinition = "text", name = "parent_id", nullable = false)
    val parentId: String,

    @Column(columnDefinition = "text", name = "job_name", nullable = false, unique = true)
    val jobName: String,

    @Column(nullable = false)
    var createdAt: OffsetDateTime,
) : PanacheEntity() {
    companion object : PanacheCompanion<BackgroundJob> {
        fun findByJobName(name: String): BackgroundJob? {
            return find("WHERE jobName = ?1", name).firstResult()
        }
    }
}
