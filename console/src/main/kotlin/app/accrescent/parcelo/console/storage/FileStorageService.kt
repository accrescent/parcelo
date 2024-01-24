// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.storage

import org.jetbrains.exposed.dao.id.EntityID
import java.io.InputStream

interface FileStorageService {
    fun saveFile(inputStream: InputStream): EntityID<Int>

    /**
     * Deletes the given file by its ID
     */
    fun deleteFile(id: EntityID<Int>)
    fun loadFile(id: EntityID<Int>): InputStream
}
