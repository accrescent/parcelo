package app.accrescent.parcelo.console.routes.auth

import kotlinx.serialization.Serializable


@Serializable
data class Permissions(
    val reviewer: Boolean,
    val publisher: Boolean
)