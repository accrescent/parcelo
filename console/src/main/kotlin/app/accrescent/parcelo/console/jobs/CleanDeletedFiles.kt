// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.jobs

import app.accrescent.parcelo.console.storage.FileStorageService
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.inject
import kotlin.getValue

/**
 * Removes all files marked deleted
 */
fun cleanDeletedFiles() {
    val storageService: FileStorageService by inject(FileStorageService::class.java)

    runBlocking {
        storageService.cleanAllFiles()
    }
}
