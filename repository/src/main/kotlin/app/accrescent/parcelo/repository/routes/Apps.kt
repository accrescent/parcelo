package app.accrescent.parcelo.repository.routes

import app.accrescent.parcelo.apksparser.ApkSetMetadata
import app.accrescent.parcelo.apksparser.InvalidApkSetException
import app.accrescent.parcelo.apksparser.parseApkSet
import app.accrescent.parcelo.repository.Config
import app.accrescent.parcelo.repository.data.net.RepoData
import app.accrescent.parcelo.repository.routes.auth.API_KEY_AUTH_PROVIDER
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.readAllParts
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.zip.ZipInputStream
import javax.imageio.IIOException
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile

fun Route.appRoutes() {
    authenticate(API_KEY_AUTH_PROVIDER) {
        createAppRoute()
    }
}

fun Route.createAppRoute() {
    val config: Config by inject()

    post("/apps") {
        val multipart = call.receiveMultipart().readAllParts()

        var apkSetData: ByteArray? = null
        var apkSetMetadata: ApkSetMetadata? = null
        var iconData: ByteArray? = null

        for (part in multipart) {
            if (part is PartData.FileItem && part.name == "apk_set") {
                apkSetMetadata = try {
                    apkSetData = part.streamProvider().use { it.readAllBytes() }
                    apkSetData.inputStream().use { parseApkSet(it) }
                } catch (e: InvalidApkSetException) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                } finally {
                    part.dispose
                }
            } else if (part is PartData.FileItem && part.name == "icon") {
                iconData = part.streamProvider().use { it.readAllBytes() }

                // Icon must be a 512 x 512 PNG
                val pngReader = ImageIO.getImageReadersByFormatName("PNG").next()
                val image = try {
                    iconData.inputStream().use { ImageIO.createImageInputStream(it) }.use {
                        pngReader.input = it
                        pngReader.read(0)
                    }
                } catch (e: IIOException) {
                    // Assume this is a format error
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                if (image.width != 512 || image.height != 512) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
            } else {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
        }

        // Publish app to the webserver
        if (apkSetData != null && apkSetMetadata != null && iconData != null) {
            apkSetData.inputStream().use { apkSet ->
                ZipInputStream(apkSet).use { zip ->
                    iconData.inputStream().use { icon ->
                        publishApp(config.publishDirectory, zip, apkSetMetadata, icon)
                    }
                }
            }

            call.respond(HttpStatusCode.OK)
        } else {
            call.respond(HttpStatusCode.BadRequest)
        }
    }
}

private fun publishApp(
    publishDir: String,
    zip: ZipInputStream,
    metadata: ApkSetMetadata,
    icon: InputStream,
) {
    val appDir = Paths.get(publishDir, metadata.appId)
    val apksDir = Paths.get(appDir.toString(), metadata.versionCode.toString())
    val baseDirAttributes = PosixFilePermissions.asFileAttribute(
        setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
        )
    )
    apksDir.createDirectories(baseDirAttributes)

    // Extract split APKs
    generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
        // Don't extract any file that doesn't have an associated split name or explicit lack thereof
        val splitName = metadata.entrySplitNames[entry.name] ?: return@forEach

        val fileName = if (splitName.isEmpty) "base.apk" else "split.${splitName.get()}.apk"
        val outFile = File(apksDir.toString(), fileName)

        FileOutputStream(outFile).use { zip.copyTo(it) }
    }

    // Copy icon
    val iconFile = File(appDir.toString(), "icon.png")
    FileOutputStream(iconFile).use { icon.copyTo(it) }

    // Publish repodata
    val repoData = RepoData(
        version = metadata.versionName,
        versionCode = metadata.versionCode,
        abiSplits = metadata.abiSplits.map { it.replace("_", "-") }.toSet(),
        langSplits = metadata.langSplits,
        densitySplits = metadata.densitySplits,
    )
    val repoDataFileAttributes = PosixFilePermissions.asFileAttribute(
        setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ,
        )
    )
    val repoDataFile = appDir.resolve("repodata.json").createFile(repoDataFileAttributes)
    repoDataFile.toFile().outputStream().use { outFile ->
        Json.encodeToString(repoData).byteInputStream().use {
            it.copyTo(outFile)
        }
    }
}
