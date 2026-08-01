// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driven.datastore

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import java.time.OffsetDateTime

sealed class AppDraft {
    abstract val id: String
    abstract val organizationId: String
    abstract val createTime: OffsetDateTime

    data class Unsubmitted(
        override val id: String,
        override val organizationId: String,
        override val createTime: OffsetDateTime,
        val defaultAppDraftListingId: Option<String>,
        val appPackageId: Option<String>,
    ) : AppDraft()

    data class Submitted(
        override val id: String,
        override val organizationId: String,
        override val createTime: OffsetDateTime,
        val defaultAppDraftListingId: String,
        val appPackageId: String,
        val submitTime: OffsetDateTime,
    ) : AppDraft()

    val optionalAppPackageId: Option<String>
        get() = when (this) {
            is Submitted -> Some(this.appPackageId)
            is Unsubmitted -> this.appPackageId
        }

    val optionalDefaultAppDraftListingId: Option<String>
        get() = when (this) {
            is Submitted -> Some(this.defaultAppDraftListingId)
            is Unsubmitted -> this.defaultAppDraftListingId
        }

    val optionalSubmitTime: Option<OffsetDateTime>
        get() = when (this) {
            is Submitted -> Some(this.submitTime)
            is Unsubmitted -> None
        }
}
