// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console

data class Config(
    val baseUrl: String,
    val databasePath: String,
    val repositoryUrl: String,
    val repositoryApiKey: String,
)
