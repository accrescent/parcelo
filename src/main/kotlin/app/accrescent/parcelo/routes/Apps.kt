package app.accrescent.parcelo.routes

import app.accrescent.parcelo.data.App as AppDao
import app.accrescent.parcelo.data.net.App
import app.accrescent.parcelo.validation.ApkSetMetadata
import app.accrescent.parcelo.validation.InvalidApkSetException
import app.accrescent.parcelo.validation.parseApkSet
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.readAllParts
import io.ktor.http.content.streamProvider
import io.ktor.resources.Resource
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.h2.api.ErrorCode
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.transactions.transaction

@Resource("/apps")
class Apps

fun Route.appRoutes() {
    createAppRoute()
    getAppsRoute()
}

fun Route.createAppRoute() {
    post("/apps") {
        var apkSetMetadata: ApkSetMetadata? = null
        var label: String? = null

        val multipart = call.receiveMultipart().readAllParts()
        for (part in multipart) {
            if (part is PartData.FileItem && part.name == "apk_set") {
                apkSetMetadata = try {
                    part.streamProvider().use { parseApkSet(it) }
                } catch (e: InvalidApkSetException) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                } finally {
                    part.dispose()
                }
            } else if (part is PartData.FormItem && part.name == "label") {
                label = part.value
            }
        }

        if (apkSetMetadata != null && label != null) {
            try {
                transaction {
                    AppDao.new(apkSetMetadata.appId) {
                        this.label = label
                        versionCode = apkSetMetadata.versionCode
                        versionName = apkSetMetadata.versionName
                    }
                }
            } catch (e: ExposedSQLException) {
                if (e.errorCode == ErrorCode.DUPLICATE_KEY_1) {
                    call.respond(HttpStatusCode.Conflict)
                } else {
                    throw e
                }
            }

            val app = App(
                id = apkSetMetadata.appId,
                label = label,
                versionCode = apkSetMetadata.versionCode,
                versionName = apkSetMetadata.versionName,
            )
            call.respond(app)
        } else {
            call.respond(HttpStatusCode.BadRequest)
        }
    }
}

fun Route.getAppsRoute() {
    get<Apps> {
        val apps = transaction {
            AppDao.all().map { App(it.id.value, it.label, it.versionCode, it.versionName) }
        }

        call.respond(apps)
    }
}
