// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.storage

import app.accrescent.parcelo.console.data.File as FileDao
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import kotlin.io.path.createFile

class LocalFileStorageService(private val baseDirectory: Path) : FileStorageService {
    override fun saveFile(inputStream: InputStream): EntityID<Int> {
        val fileAttributes = PosixFilePermissions.asFileAttribute(
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
            )
        )
        val path = baseDirectory.resolve(UUID.randomUUID().toString()).createFile(fileAttributes)
        path.toFile().outputStream().use { inputStream.copyTo(it) }

        return transaction { FileDao.new { localPath = path.toString() }.id }
    }

    override fun deleteFile(id: EntityID<Int>): Boolean {
        val path = getPathForFile(id) ?: throw FileNotFoundException()
        if (!File(path).delete()) {
            return false
        }

        transaction { FileDao.findById(id)?.delete() }

        return true
    }

    override fun loadFile(id: EntityID<Int>): InputStream {
        val path = getPathForFile(id) ?: throw FileNotFoundException()

        return File(path).inputStream()
    }

    private fun getPathForFile(id: EntityID<Int>) = transaction { FileDao.findById(id)?.localPath }
}
