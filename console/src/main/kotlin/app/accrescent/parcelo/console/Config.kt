// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console

data class Config(
    val application: Application,
    val repository: Repository,
    val github: GitHub,
) {
    data class Application(
        val baseUrl: String,
        val databasePath: String,
        val fileStorageDir: String,
    )

    data class Repository(
        val url: String,
        val apiKey: String,
    )

    data class GitHub(
        val clientId: String,
        val clientSecret: String,
        val redirectUrl: String,
    )
}