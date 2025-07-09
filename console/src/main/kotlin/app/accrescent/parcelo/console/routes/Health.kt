// Copyright 2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.routes

import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

@Resource("/")
class Health

fun Route.healthRoutes() {
    basicHealthRoute()
}

fun Route.basicHealthRoute() {
    get<Health> {
        call.respond(HttpStatusCode.OK)
    }
}
