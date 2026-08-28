// SPDX-FileCopyrightText: © 2026 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.domain.ports.driven.datastore

import app.accrescent.server.parcelo.core.Bytes
import app.accrescent.server.parcelo.core.text.UString
import app.accrescent.server.parcelo.domain.android.ApplicationId
import app.accrescent.server.parcelo.domain.android.SdkVersion
import app.accrescent.server.parcelo.domain.android.VersionCode
import app.accrescent.server.parcelo.domain.android.VersionName
import java.time.OffsetDateTime

data class AppPackage(
    val id: UString,
    val appDraftId: UString,
    val externalBlobId: UString,
    val uploadEventTime: OffsetDateTime,
    val appId: ApplicationId,
    val versionCode: VersionCode,
    val versionName: VersionName,
    val targetSdk: SdkVersion,
    val signerCertificate: Bytes,
    val buildApksResult: Bytes,
)
