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
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "app_draft_listing_icon_upload_jobs")
class AppDraftListingIconUploadJob(
    @Column(name = "app_draft_listing_id", nullable = false)
    val appDraftListingId: UUID,

    @Column(name = "upload_key", nullable = false, unique = true)
    val uploadKey: UUID,

    @Column(name = "completed", nullable = false)
    var completed: Boolean,

    @Column(name = "succeeded", nullable = false)
    var succeeded: Boolean,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: OffsetDateTime,
) : PanacheEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_draft_listing_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    lateinit var appDraftListing: AppDraftListing

    companion object : PanacheCompanion<AppDraftListingIconUploadJob> {
        fun findByUploadKey(uploadKey: UUID): AppDraftListingIconUploadJob? {
            return find("WHERE uploadKey = ?1", uploadKey).firstResult()
        }

        fun markFailed(uploadKey: UUID) {
            update("SET completed = true, succeeded = false WHERE uploadKey = ?1", uploadKey)
        }

        fun markSucceeded(uploadKey: UUID) {
            update("SET completed = true, succeeded = true WHERE uploadKey = ?1", uploadKey)
        }
    }
}
