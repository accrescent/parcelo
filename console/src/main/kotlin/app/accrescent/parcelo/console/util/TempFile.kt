// Copyright 2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.util

import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.AutoCloseable
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlin.Long
import kotlin.arrayOf
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting

/**
 * A temporary file which can close after its scope via the [AutoCloseable] interface
 *
 * @property path the path of the underlying file
 */
class TempFile : AutoCloseable {
    val path: Path

    init {
        val fileAttributes = PosixFilePermissions.asFileAttribute(
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
            )
        )
        path = createTempFile(attributes = arrayOf(fileAttributes))
    }

    /**
     * Constructs a new [FileInputStream] of this file and returns it as a result
     */
    fun inputStream(): FileInputStream {
        return path.toFile().inputStream()
    }

    /**
     * Constructs a new [FileOutputStream] of this file and returns it as a result
     */
    fun outputStream(): FileOutputStream {
        return path.toFile().outputStream()
    }

    /**
     * Returns the size of this file in bytes
     */
    fun size(): Long {
        return path.toFile().length()
    }

    /**
     * Deletes the underlying file
     */
    override fun close() {
        path.deleteExisting()
    }
}
