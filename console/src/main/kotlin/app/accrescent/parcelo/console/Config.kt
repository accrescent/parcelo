// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console

data class Config(
    val application: Application,
    val s3: S3,
    val github: GitHub,
) {
    data class Application(
        val baseUrl: String,
        val databasePath: String,
        val fileStorageDir: String,
    )

    data class S3(
        val endpointUrl: String,
        val region: String,
        val bucket: String,
        val accessKeyId: String,
        val secretAccessKey: String,
    )

    data class GitHub(
        val clientId: String,
        val clientSecret: String,
        val redirectUrl: String,
    )
}
