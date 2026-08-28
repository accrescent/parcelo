// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driven.datastore

import app.accrescent.server.parcelo.core.text.UString
import arrow.core.Option
import java.time.OffsetDateTime

sealed class AppDraftApiView {
    abstract val id: UString
    abstract val createTime: OffsetDateTime

    data class Unsubmitted(
        override val id: UString,
        override val createTime: OffsetDateTime,
        val defaultAppDraftListingId: Option<UString>,
        val appPackage: Option<AppPackageApiView>,
    ) : AppDraftApiView()

    data class Submitted(
        override val id: UString,
        override val createTime: OffsetDateTime,
        val defaultAppDraftListingId: UString,
        val appPackage: AppPackageApiView,
        val submitTime: OffsetDateTime,
    ) : AppDraftApiView()
}
