// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.repository.routes

import app.accrescent.parcelo.apksparser.ApkSet
import app.accrescent.parcelo.apksparser.ParseApkSetResult
import app.accrescent.parcelo.repository.Config
import app.accrescent.parcelo.repository.data.net.RepoData
import app.accrescent.parcelo.repository.routes.auth.API_KEY_AUTH_PROVIDER
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.readAllParts
import io.ktor.http.content.streamProvider
import io.ktor.resources.Resource
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveMultipart
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
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
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteRecursively
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.setPosixFilePermissions

@Resource("/apps")
class Apps {
    @Resource("{id}")
    class Id(val parent: Apps = Apps(), val id: String)
}

fun Route.appRoutes() {
    authenticate(API_KEY_AUTH_PROVIDER) {
        createAppRoute()
        updateAppRoute()
    }
}

fun Route.createAppRoute() {
    val config: Config by inject()

    post<Apps> {
        val multipart = call.receiveMultipart().readAllParts()

        var apkSetData: ByteArray? = null
        var apkSet: ApkSet? = null
        var iconData: ByteArray? = null

        for (part in multipart) {
            if (part is PartData.FileItem && part.name == "apk_set") {
                val parseResult = run {
                    apkSetData = part.streamProvider().use { it.readBytes() }
                    apkSetData!!.inputStream().use { ApkSet.parse(it) }
                }
                part.dispose()
                apkSet = when (parseResult) {
                    is ParseApkSetResult.Ok -> parseResult.apkSet
                    is ParseApkSetResult.Error -> run {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }
                }
            } else if (part is PartData.FileItem && part.name == "icon") {
                iconData = part.streamProvider().use { it.readBytes() }

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
        if (apkSetData != null && apkSet != null && iconData != null) {
            apkSetData!!.inputStream().use { apkSetInputStream ->
                ZipInputStream(apkSetInputStream).use { zip ->
                    iconData.inputStream().use { icon ->
                        publish(config.publishDirectory, zip, apkSet, PublicationType.NewApp(icon))
                    }
                }
            }

            call.respond(HttpStatusCode.OK)
        } else {
            call.respond(HttpStatusCode.BadRequest)
        }
    }
}

fun Route.updateAppRoute() {
    val config: Config by inject()

    put<Apps.Id> {
        val multipart = call.receiveMultipart().readAllParts()

        var apkSetData: ByteArray? = null
        var apkSet: ApkSet? = null

        for (part in multipart) {
            if (part is PartData.FileItem && part.name == "apk_set") {
                val parseResult = run {
                    apkSetData = part.streamProvider().use { it.readBytes() }
                    apkSetData!!.inputStream().use { ApkSet.parse(it) }
                }
                part.dispose()
                apkSet = when (parseResult) {
                    is ParseApkSetResult.Ok -> parseResult.apkSet
                    is ParseApkSetResult.Error -> run {
                        call.respond(HttpStatusCode.BadRequest)
                        return@put
                    }
                }
            } else {
                call.respond(HttpStatusCode.BadRequest)
                return@put
            }
        }

        // Publish update to the webserver
        apkSetData!!.inputStream().use { apkSetInputStream ->
            ZipInputStream(apkSetInputStream).use { zip ->
                publish(config.publishDirectory, zip, apkSet!!, PublicationType.Update)
            }
        }

        call.respond(HttpStatusCode.OK)
    }
}

private sealed class PublicationType {
    class NewApp(val icon: InputStream) : PublicationType()
    data object Update : PublicationType()
}

@OptIn(ExperimentalPathApi::class)
private fun publish(
    publishDir: String,
    zip: ZipInputStream,
    metadata: ApkSet,
    type: PublicationType,
) {
    val appDir = Paths.get(publishDir, metadata.appId.value)
    val apksDir = Paths.get(appDir.toString(), metadata.versionCode.toString())
    apksDir
        .createDirectories()
        .setPosixFilePermissions(
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_EXECUTE,
            )
        )

    // Extract split APKs
    generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
        // Don't extract any file that doesn't have an associated split name or explicit lack thereof
        val splitName = metadata.entrySplitNames[entry.name] ?: return@forEach

        val fileName = if (splitName.isEmpty) "base.apk" else "split.${splitName.get()}.apk"

        File(apksDir.toString(), fileName).apply {
            FileOutputStream(this).use { zip.copyTo(it) }
            setReadable(true, false)
        }
    }

    // Copy icon
    if (type is PublicationType.NewApp) {
        File(appDir.toString(), "icon.png").apply {
            FileOutputStream(this).use { type.icon.copyTo(it) }
            setReadable(true, false)
        }
    }

    // Publish repodata
    val repoData = RepoData(
        version = metadata.versionName,
        versionCode = metadata.versionCode,
        abiSplits = metadata.abiSplits.map { it.replace("_", "-") }.toSet(),
        langSplits = metadata.langSplits,
        densitySplits = metadata.densitySplits,
    )
    val repoDataFile = appDir.resolve("repodata.json").apply {
        if (type is PublicationType.NewApp) {
            val path = createFile()
            File(path.toString()).setReadable(true, false)
        }
    }
    repoDataFile.toFile().outputStream().use { outFile ->
        Json.encodeToString(repoData).byteInputStream().use {
            it.copyTo(outFile)
        }
    }

    // Delete old split APKs
    if (type is PublicationType.Update) {
        appDir.forEachDirectoryEntry {
            if (!it.isDirectory()) return@forEachDirectoryEntry

            val directoryVersionCode = it.name.toIntOrNull() ?: return@forEachDirectoryEntry
            if (directoryVersionCode < metadata.versionCode) {
                it.deleteRecursively()
            }
        }
    }
}
