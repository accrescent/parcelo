// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.repository

data class Config(
    val databasePath: String,
    val publishDirectory: String,
    val repositoryApiKey: String,
)
