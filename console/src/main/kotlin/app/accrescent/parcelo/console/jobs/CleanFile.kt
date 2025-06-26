// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.jobs

import app.accrescent.parcelo.console.storage.ObjectStorageService
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.inject
import kotlin.getValue

/**
 * Removes the file with the given ID if it's marked deleted
 */
fun cleanFile(fileId: Int) {
    val storageService: ObjectStorageService by inject(ObjectStorageService::class.java)

    runBlocking {
        storageService.cleanObject(fileId)
    }
}
