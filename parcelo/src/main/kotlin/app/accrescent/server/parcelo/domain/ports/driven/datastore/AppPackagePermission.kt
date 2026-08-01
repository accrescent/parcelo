// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driven.datastore

import app.accrescent.server.parcelo.domain.android.NameAttribute
import app.accrescent.server.parcelo.domain.android.SdkVersion
import arrow.core.Option

data class AppPackagePermission(
    val id: String,
    val appPackageId: String,
    val name: NameAttribute,
    val maxSdkVersion: Option<SdkVersion>,
)
