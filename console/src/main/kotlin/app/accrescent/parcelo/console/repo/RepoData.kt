// Copyright 2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.repo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * App-specific repository metadata
 *
 * @property version the app's version name
 * @property versionCode the app's version code
 * @property abiSplits the set of ABI split APKs the app provides
 * @property densitySplits the set of screen density split APKs the app provides
 * @property langSplits the set of language split APKs the app provides
 * @property shortDescription the default short description of the app
 */
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
