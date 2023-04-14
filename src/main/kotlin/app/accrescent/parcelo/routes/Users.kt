package app.accrescent.parcelo.routes

import app.accrescent.parcelo.data.User
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.application.call
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

@Resource("/users")
class Users {
    @Resource("{id}")
    class Id(val parent: Users = Users(), val id: String)
}

fun Route.userRoutes() {
    getUserRoute()
    getUsersRoute()
}

fun Route.getUserRoute() {
    get<Users.Id> {
        val userId = try {
            UUID.fromString(it.id)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }

        val user = transaction { User.findById(userId) }?.serializable()
        if (user == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(user)
        }
    }
}

fun Route.getUsersRoute() {
    get<Users> {
        val users = transaction { User.all().map { it.serializable() } }

        call.respond(users)
    }
}
