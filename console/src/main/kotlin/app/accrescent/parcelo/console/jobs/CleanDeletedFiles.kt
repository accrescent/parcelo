// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.jobs

import app.accrescent.parcelo.console.data.File as FileDao
import app.accrescent.parcelo.console.data.Files
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

/**
 * Removes all files marked deleted
 */
fun cleanDeletedFiles() {
    transaction { FileDao.find { Files.deleted eq true } }.forEach {
        if (File(it.localPath).delete()) {
            transaction { it.delete() }
        }
    }
}
