// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console

data class Config(
    val application: Application,
    val cors: Cors,
    val postgresql: Postgresql,
    val privateStorage: S3,
    val s3: S3,
    val github: GitHub,
) {
    data class Application(val baseUrl: String)

    data class Cors(val allowedHost: String, val allowedScheme: String)

    data class Postgresql(
        val serverName: String,
        val databaseName: String,
        val portNumber: Int,
        val user: String,
        val password: String,
        val ssl: Boolean,
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
