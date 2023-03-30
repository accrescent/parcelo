package app.accrescent.parcelo.routes.auth

import io.ktor.server.auth.Principal

data class Session(val id: String) : Principal
