// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Edit(
    val id: String,
    @SerialName("app_id")
    val appId: String,
    @SerialName("short_description")
    val shortDescription: String?,
    @SerialName("creation_time")
    val creationTime: Long,
    val status: EditStatus,
)

@Serializable
enum class EditStatus {
    @SerialName("unsubmitted")
    UNSUBMITTED,

    @SerialName("submitted")
    SUBMITTED,
}
