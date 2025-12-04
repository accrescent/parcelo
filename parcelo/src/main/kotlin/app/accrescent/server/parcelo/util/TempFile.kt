// SPDX-FileCopyrightText: © 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.parcelo.util

import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists

class TempFile(directory: Path) : AutoCloseable {
    val path: Path

    init {
        val filePermissions = PosixFilePermissions
            .asFileAttribute(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        path = createTempFile(directory = directory, attributes = arrayOf(filePermissions))
    }

    override fun close() {
        path.deleteIfExists()
    }
}
