// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.jobs

import app.accrescent.parcelo.console.storage.FileStorageService
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.inject
import kotlin.getValue

/**
 * Removes the file with the given ID if it's marked deleted
 */
fun cleanFile(fileId: Int) {
    val storageService: FileStorageService by inject(FileStorageService::class.java)

    runBlocking {
        storageService.cleanFile(fileId)
    }
}
