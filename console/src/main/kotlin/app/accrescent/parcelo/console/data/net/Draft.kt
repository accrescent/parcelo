// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Draft(
    val id: String,
    @SerialName("app_id")
    val appId: String,
    val label: String,
    @SerialName("version_code")
    val versionCode: Int,
    @SerialName("version_name")
    val versionName: String,
    @SerialName("creation_time")
    val creationTime: Long,
    val status: DraftStatus,
)

@Serializable
enum class DraftStatus {
    @SerialName("unsubmitted")
    UNSUBMITTED,

    @SerialName("submitted")
    SUBMITTED,

    @SerialName("approved")
    APPROVED,

    @SerialName("rejected")
    REJECTED,

    @SerialName("publishing")
    PUBLISHING,
}
