// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.repository.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RepoData(
    val version: String,
    @SerialName("version_code")
    val versionCode: Int,
    @SerialName("abi_splits")
    val abiSplits: Set<String>,
    @SerialName("density_splits")
    val densitySplits: Set<String>,
    @SerialName("lang_splits")
    val langSplits: Set<String>,
    @SerialName("short_description")
    val shortDescription: String? = null,
)
